package dev.projectredacted.room;

import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.Areas;
import dev.projectredacted.util.ModConfig;
import dev.projectredacted.util.Msg;
import dev.projectredacted.util.Scheduler;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Rooms made by key blocks in survival (site generation v4 §5.8): a player builds a closed room by hand and
 * places the key block inside, and the space becomes that room. Breaking the key block (after a warning)
 * un-designates it.
 */
public final class KeyRooms {
    /** Player -> the key block they were warned about, and when. */
    private static final Map<UUID, long[]> WARNED = new HashMap<>();

    public static void init() {
        // Placement: check a tick later, once the block is in the world.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!(player.getItemInHand(hand).getItem() instanceof BlockItem item) || KeyBlocks.of(item.getBlock()) == null) return InteractionResult.PASS;
            BlockPos clicked = hit.getBlockPos().immutable(), beside = clicked.relative(hit.getDirection());
            Scheduler.next(() -> {
                for (BlockPos p : List.of(beside, clicked)) {
                    if (level.getBlockState(p).is(item.getBlock())) {
                        placed(sp, (ServerLevel) level, p);
                        return;
                    }
                }
            });
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> level.isClientSide() || confirmBreak(level, player, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) return;
            if (state.is(dev.projectredacted.registry.ModBlocks.AREA_HALL_MARKER)) {
                Site site = SiteManager.at(level, pos);
                if (site != null) dev.projectredacted.site.gen.HandHalls.remove(site, pos);
                return;
            }
            broken((ServerLevel) level, pos, state);
        });
    }

    // ---------------------------------------------------------------- placing

    /** A key block was just placed at {@code pos}. */
    public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        KeyBlocks.Key key = KeyBlocks.of(state);
        RoomType type = key == null ? null : RoomTypes.get(key.roomType());
        if (type == null) return;
        Site site = SiteManager.at(level, pos);
        if (site == null || !site.canBuild(player)) return;
        String problem = designate(site, level, pos, key, type, player);
        if (problem != null) Msg.actionBar(player, problem, ChatFormatting.YELLOW);
    }

    /**
     * Makes the closed space around the key block at {@code pos} into a room. Returns why it didn't (the key block
     * then stays as just a block, V20), or null once the room is registered.
     */
    public static String designate(Site site, ServerLevel level, BlockPos pos, KeyBlocks.Key key, RoomType type, Player player) {
        String name = type.name;
        // Already a room (V20): another key block's room, or a room from before key blocks around this spot.
        RoomShape.Result space = RoomShape.fill(RoomShape.world(level, pos, ModConfig.get().roomScanSize), RoomShape.around(pos));
        Room existing = roomOf(site, space, pos);
        if (existing != null) {
            if (existing.type.equals(type.id)) return null; // one more Bunk Bed in a Barracks
            return "Already " + existing.displayName() + ".";
        }
        if (!space.closed()) {
            showLeak(level, space);
            return space.tooBig() || space.leak() == null ? "No closed room around this " + blockName(level, pos) + " within " + ModConfig.get().roomScanSize + " blocks."
                    : "This room isn't closed: it leaks at " + space.leak().toShortString() + ".";
        }
        int[] b = space.box();
        int size = ModConfig.get().roomScanSize;
        if (b[3] - b[0] + 1 > size || b[5] - b[2] + 1 > size || b[4] - b[1] + 1 > size) return "That room is bigger than the " + size + "-block scan area.";
        // Two different rooms in one space.
        for (long w : space.walls()) {
            BlockPos wp = BlockPos.of(w);
            if (wp.equals(pos)) continue;
            KeyBlocks.Key other = KeyBlocks.of(level.getBlockState(wp));
            if (other != null && !other.roomType().equals(key.roomType())) return "Two rooms in one space: wall them off.";
        }
        Room room = new Room(0, type.id, new BlockPos(b[0], b[1], b[2]), new BlockPos(b[3], b[4], b[5]), 0, level.getRandom().nextLong());
        if (!site.containsRoom(room)) return "The room must be inside your claim.";
        String locked = RoomPlanner.lockedReason(site, type);
        if (locked != null) return name + ": " + locked + ".";
        // The wrong area (V20): a Bunk Bed in Containment is just a block.
        String kind = Areas.areaOf(type.id);
        String area = Areas.areaAt(site, room.center());
        if (area != null && !Areas.kindOf(area).equals(kind)) {
            Areas.Area def = Areas.get(kind);
            return name + " belongs in " + (def == null ? kind : def.name) + ", not " + Areas.displayName(site, area) + ".";
        }
        room.number = site.nextRoomNumber++;
        room.freeform = true;
        room.built = true;
        room.key = pos.immutable();
        if (key.scp() != null) {
            var def = dev.projectredacted.site.gen.PartKit.scpDefinition(key.scp());
            room.scpLabel = def != null && def.number != null ? def.number : key.scp().toUpperCase(Locale.ROOT).replace('_', '-');
        }
        room.area = area;
        RoomPlanner.name(site, room, type);
        Areas.assign(site, room);
        room.findEntrance(level);
        site.rooms.add(room);
        linkObservation(site, room);
        String msg = room.displayName() + " registered.";
        if (!Cassie.announce(site, msg) && player != null) Msg.send(player, msg, ChatFormatting.GREEN);
        site.log(msg);
        return null;
    }

    /** A hand-built Observation Room sharing a wall with a cell watches it (§5.5), whichever is registered first. */
    private static void linkObservation(Site site, Room room) {
        for (Room o : site.rooms) {
            if (o == room || !touches(o, room)) continue;
            Room obs = room.type.equals(dev.projectredacted.site.gen.SiteGen.OBSERVATION_ROOM) ? room : o.type.equals(dev.projectredacted.site.gen.SiteGen.OBSERVATION_ROOM) ? o : null;
            Room cell = obs == room ? o : room;
            if (obs == null || obs == cell || obs.watches != 0 || cell.typeDef() == null || !cell.typeDef().isContainment()) continue;
            obs.watches = cell.number;
        }
    }

    private static boolean touches(Room a, Room b) {
        return a.x1 <= b.x2 + 1 && a.x2 >= b.x1 - 1 && a.y1 <= b.y2 + 1 && a.y2 >= b.y1 - 1 && a.z1 <= b.z2 + 1 && a.z2 >= b.z1 - 1;
    }

    /** The registered room this space (or, for rooms from before key blocks, this spot) belongs to. */
    private static Room roomOf(Site site, RoomShape.Result space, BlockPos pos) {
        for (Room r : site.rooms) {
            if (r.key != null) {
                if (r.key.equals(pos)) return r;
                for (BlockPos n : RoomShape.around(r.key)) if (space.contains(n)) return r;
            } else if (pos.getX() > r.x1 && pos.getX() < r.x2 && pos.getY() > r.y1 && pos.getY() < r.y2 && pos.getZ() > r.z1 && pos.getZ() < r.z2) {
                return r;
            }
        }
        return null;
    }

    private static void showLeak(ServerLevel level, RoomShape.Result space) {
        if (space.leak() == null) return;
        BlockPos l = space.leak();
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, l.getX() + 0.5, l.getY() + 0.5, l.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
    }

    private static String blockName(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    // ---------------------------------------------------------------- the label

    /** Runs every 20 ticks: a player looking at a room's key block sees the room's label (§5.8). */
    public static void tickHud(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (dev.projectredacted.site.gen.AutoPlace.get(p) != null) continue;
            if (!(p.pick(p.blockInteractionRange(), 0f, false) instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) continue;
            BlockPos pos = hit.getBlockPos();
            if (!KeyBlocks.isKey(p.level().getBlockState(pos))) continue;
            Site site = SiteManager.at(p.level(), pos);
            Room room = site == null ? null : site.roomWithKey(pos);
            if (room == null) {
                if (site != null) for (Room r : site.rooms) if (r.key != null && r.contains(pos)) room = r;
            }
            if (room == null) continue;
            String text = room.displayName().toUpperCase(java.util.Locale.ROOT);
            if (room.watches != 0) {
                Room cell = site.room(room.watches);
                if (cell != null) text += " · watches " + cell.displayName();
            }
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(text).withStyle(room.working() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }

    // ---------------------------------------------------------------- breaking

    /** The first break of a room's key block only warns (§5.8); breaking it again within 10 seconds goes ahead. */
    private static boolean confirmBreak(Level level, Player player, BlockPos pos) {
        Site site = SiteManager.at(level, pos);
        Room room = site == null ? null : site.roomWithKey(pos);
        if (room == null || otherKey(level, room, pos) != null) return true;
        long now = level.getGameTime();
        long[] w = WARNED.get(player.getUUID());
        if (w != null && w[0] == pos.asLong() && now - w[1] < 200) {
            WARNED.remove(player.getUUID());
            return true;
        }
        WARNED.put(player.getUUID(), new long[]{pos.asLong(), now});
        Msg.actionBar(player, "This will stop " + room.displayName() + " working. Break it again to confirm.", ChatFormatting.GOLD);
        return false;
    }

    /** A key block is gone: its room moves to another key block of its type in the space, or is un-designated. */
    public static void broken(ServerLevel level, BlockPos pos, BlockState old) {
        Site site = SiteManager.at(level, pos);
        Room room = site == null ? null : site.roomWithKey(pos);
        if (room == null) return;
        BlockPos other = otherKey(level, room, pos);
        if (other != null) {
            room.key = other;
            return;
        }
        site.removeRoom(room);
        String msg = room.displayName() + " is no longer a room.";
        site.log(msg);
        Cassie.announce(site, msg);
    }

    /** Another key block of the room's type inside its box (more Bunk Beds in a Barracks). */
    private static BlockPos otherKey(Level level, Room room, BlockPos except) {
        RoomType t = room.typeDef();
        var block = t == null ? null : t.keyBlockDef();
        if (block == null) return null;
        for (BlockPos p : BlockPos.betweenClosed(room.x1, room.y1, room.z1, room.x2, room.y2, room.z2)) {
            if (!p.equals(except) && level.getBlockState(p).is(block)) return p.immutable();
        }
        return null;
    }

    private KeyRooms() {}
}
