package dev.projectredacted.room;

import dev.projectredacted.site.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.*;

/**
 * Work stations inside rooms (NPC revamp §4.2): the room's key block and the blocks its room type lists as
 * stations. Staff stand on the open spot beside a station, one per station; when all are taken, they stand
 * somewhere free inside the room.
 */
public final class Stations {
    /** A station block and where an NPC stands to use it. */
    public record Station(BlockPos block, BlockPos stand) {}

    private record Cached(long at, List<Station> stations, List<BlockPos> free) {}

    private static final Map<String, Cached> CACHE = new HashMap<>();
    /** site:room → stand spot → who has it, and until when. */
    private static final Map<String, Map<BlockPos, Claim>> CLAIMS = new HashMap<>();

    private record Claim(UUID who, long until) {}

    /** Rescans a room's stations this often (ticks). */
    private static final int RESCAN = 600;
    /** A claim lapses if its NPC stops renewing it. */
    private static final int CLAIM_TICKS = 100;

    /**
     * The spot {@code who} should work at in {@code room}: the station it already has, else a free one, else a free
     * spot inside the room. Null when the room has no open space at all (its center is used then).
     */
    public static Station claim(ServerLevel level, Site site, Room room, UUID who) {
        String key = site.id + ":" + room.number;
        Cached c = scan(level, key, room);
        long now = level.getGameTime();
        Map<BlockPos, Claim> claims = CLAIMS.computeIfAbsent(key, k -> new HashMap<>());
        claims.values().removeIf(cl -> cl.until < now);
        for (Map.Entry<BlockPos, Claim> e : claims.entrySet()) {
            if (!e.getValue().who.equals(who)) continue;
            e.setValue(new Claim(who, now + CLAIM_TICKS));
            for (Station s : c.stations) if (s.stand.equals(e.getKey())) return s;
            return new Station(null, e.getKey());
        }
        for (Station s : c.stations) {
            if (claims.containsKey(s.stand)) continue;
            claims.put(s.stand, new Claim(who, now + CLAIM_TICKS));
            return s;
        }
        for (BlockPos p : c.free) {
            if (claims.containsKey(p)) continue;
            claims.put(p, new Claim(who, now + CLAIM_TICKS));
            return new Station(null, p);
        }
        return c.free.isEmpty() ? null : new Station(null, c.free.get(Math.floorMod(who.hashCode(), c.free.size())));
    }

    /** Lets go of whatever {@code who} holds in other rooms (they've moved on). */
    public static void release(Site site, UUID who, int keepRoom) {
        String keep = site.id + ":" + keepRoom;
        for (Map.Entry<String, Map<BlockPos, Claim>> e : CLAIMS.entrySet()) {
            if (!e.getKey().equals(keep)) e.getValue().values().removeIf(cl -> cl.who.equals(who));
        }
    }

    public static List<Station> stations(ServerLevel level, Site site, Room room) {
        return scan(level, site.id + ":" + room.number, room).stations;
    }

    public static void clear() {
        CACHE.clear();
        CLAIMS.clear();
    }

    private static Cached scan(ServerLevel level, String key, Room room) {
        long now = level.getGameTime();
        Cached c = CACHE.get(key);
        if (c != null && now - c.at < RESCAN) return c;
        List<Station> stations = new ArrayList<>();
        List<BlockPos> free = new ArrayList<>();
        Matcher match = matcher(room.typeDef());
        if (level.isLoaded(room.center())) {
            for (BlockPos p : BlockPos.betweenClosed(room.x1 + 1, room.y1 + 1, room.z1 + 1, room.x2 - 1, room.y2 - 1, room.z2 - 1)) {
                BlockState s = level.getBlockState(p);
                if (match.test(s)) {
                    BlockPos stand = standBeside(level, room, p.immutable(), s);
                    if (stand != null) stations.add(new Station(p.immutable(), stand));
                } else if (standable(level, p) && p.getY() <= room.y1 + 2) {
                    free.add(p.immutable());
                }
            }
        }
        // Spread the free spots out, so people don't bunch up in one corner.
        Collections.shuffle(free, new Random(key.hashCode()));
        if (free.size() > 24) free = new ArrayList<>(free.subList(0, 24));
        c = new Cached(now, stations, free);
        CACHE.put(key, c);
        return c;
    }

    /** The open spot in front of a station (the way it faces, if it faces), else any open side. */
    private static BlockPos standBeside(ServerLevel level, Room room, BlockPos block, BlockState state) {
        List<Direction> order = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            order.remove(f);
            order.addFirst(f);
        }
        for (Direction d : order) {
            BlockPos p = block.relative(d);
            if (room.contains(p) && standable(level, p)) return p;
        }
        // Beds and low blocks: stand on top is no good, so try one lower too (a bunk's lower step).
        for (Direction d : order) {
            BlockPos p = block.relative(d).below();
            if (room.contains(p) && p.getY() > room.y1 && standable(level, p)) return p;
        }
        return null;
    }

    /** Room for a person: open feet and head, and something to stand on. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        BlockState feet = level.getBlockState(p), head = level.getBlockState(p.above()), below = level.getBlockState(p.below());
        return feet.getCollisionShape(level, p).isEmpty() && head.getCollisionShape(level, p.above()).isEmpty()
                && !below.getCollisionShape(level, p.below()).isEmpty() && feet.getFluidState().isEmpty();
    }

    private interface Matcher {
        boolean test(BlockState s);
    }

    private static Matcher matcher(RoomType type) {
        Set<Block> blocks = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        if (type != null) {
            Block key = type.keyBlockDef();
            if (key != null) blocks.add(key);
            if (type.stations != null) {
                for (String id : type.stations) {
                    if (id.startsWith("#")) {
                        Identifier rl = Identifier.tryParse(id.substring(1));
                        if (rl != null) tags.add(TagKey.create(Registries.BLOCK, rl));
                    } else {
                        Identifier rl = Identifier.tryParse(id);
                        if (rl != null) BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(blocks::add);
                    }
                }
            }
        }
        return s -> {
            if (blocks.contains(s.getBlock())) return true;
            for (TagKey<Block> t : tags) if (s.is(t)) return true;
            return false;
        };
    }

    private Stations() {}
}
