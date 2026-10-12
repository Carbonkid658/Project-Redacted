package dev.projectredacted.raid;

import dev.projectredacted.api.ObjectClass;
import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.*;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Raids. Chance rises with held SCPs (by class and count), staff count and defections, and falls with security.
 * Raiders break in through the entrance, then go after their faction's objectives. A cooldown follows each raid.
 */
public final class RaidManager {
    private static final Random RANDOM = new Random();
    private static final Map<String, Long> MTF_CALL_COOLDOWN = new HashMap<>();
    public static final int MTF_DELAY = 10 * 60 * 20;

    public static void tick(ServerLevel level, Site site, int tick) {
        if (site.raid == null) {
            if (tick % 1200 == 0) maybeStart(level, site);
            return;
        }
        if (tick % 20 == 0) tickRaid(level, site);
    }

    // ---------------------------------------------------------------- starting

    public static double chancePerMinute(Site site) {
        double c = 0.002 + 0.006 * site.scpWeight + 0.0006 * site.housedStaff() + 0.004 * site.defections;
        int security = site.countStaff(StaffRole.SECURITY);
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t == null || !r.working()) continue;
            if (t.isCheckpoint()) security += 2;
            if (r.type.equals("projectredacted:armory")) security += 3;
        }
        c *= Math.max(0.25, 1 - 0.05 * security);
        if (site.hasUpgrade(Upgrade.SECURITY_INFRASTRUCTURE)) c *= 0.8;
        return Math.min(0.08, c);
    }

    private static void maybeStart(ServerLevel level, Site site) {
        if (!site.generated || level.getGameTime() < site.raidCooldownUntil) return;
        if (site.scpCount == 0 && site.housedStaff() == 0) return;
        if (Cassie.playersInSite(site).isEmpty()) return; // only raid while someone is there to defend
        if (RANDOM.nextDouble() < chancePerMinute(site)) start(level, site, null);
    }

    public static void start(ServerLevel level, Site site, Faction forced) {
        RaidState raid = new RaidState();
        if (forced != null) raid.factions.add(forced);
        else {
            Faction first = pickFaction(site, null);
            raid.factions.add(first);
            if (RANDOM.nextDouble() < 0.15) {
                Faction second = pickFaction(site, first);
                if (second != null) raid.factions.add(second);
            }
        }
        raid.startTick = level.getGameTime();
        raid.spawn = spawnPoint(level, site);
        boolean warned = Cassie.announce(site, "Attention all personnel. Hostile forces detected approaching the facility. "
                + "Estimated arrival in 60 seconds. Security personnel, report to the entrance.");
        raid.arriveTick = raid.startTick + (warned ? 1200 : 100);
        site.raid = raid;
        site.log("Raid incoming: " + raid.factions);
    }

    private static Faction pickFaction(Site site, Faction exclude) {
        Map<Faction, Double> weights = new EnumMap<>(Faction.class);
        // Default for the open question: GOC raids only care about SCPs; other factions also weigh staff count.
        weights.put(Faction.GOC, 2.0 * site.scpWeight);
        weights.put(Faction.SERPENTS_HAND, 1 + site.scpWeight + site.housedStaff() / 4.0);
        weights.put(Faction.CHAOS_INSURGENCY, 1 + site.scpWeight + site.housedStaff() / 4.0 + site.defections * 2.0);
        if (exclude != null) weights.remove(exclude);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return null;
        double roll = RANDOM.nextDouble() * total;
        for (Map.Entry<Faction, Double> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll <= 0) return e.getKey();
        }
        return Faction.CHAOS_INSURGENCY;
    }

    private static BlockPos spawnPoint(ServerLevel level, Site site) {
        Room entrance = site.firstEntrance();
        BlockPos base = entrance != null ? entrance.outsideDoor() : site.center();
        Direction dir = entrance != null ? entrance.doorSide() : Direction.SOUTH;
        BlockPos p = base.relative(dir, 28);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        return new BlockPos(p.getX(), y, p.getZ());
    }

    // ---------------------------------------------------------------- during

    private static void tickRaid(ServerLevel level, Site site) {
        RaidState raid = site.raid;
        long now = level.getGameTime();
        if (!raid.spawned) {
            if (now >= raid.arriveTick) spawnRaiders(level, site, raid);
            return;
        }
        raid.raiders.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });

        Room entrance = site.firstEntrance();
        if (!raid.alarmed) {
            BlockPos at = entrance != null ? entrance.center() : site.mainframe;
            for (UUID id : raid.raiders) {
                Entity e = level.getEntity(id);
                if (e != null && (e.blockPosition().distSqr(at) < 20 * 20 || site.roomAt(e.blockPosition()) != null)) {
                    raid.alarmed = true;
                    Cassie.alarm(site, "Intruders at " + (entrance != null ? entrance.displayName() : "the facility") + "! " + raid.factions.size() + " hostile group(s).");
                    break;
                }
            }
        }

        // Hacking C.A.S.S.I.E.: Serpent's Hand and GOC raiders standing at it make progress; leaving pauses it.
        if (site.cassie != null && !site.cassieDisabled) {
            int before = site.cassieHackStage();
            for (UUID id : raid.raiders) {
                if (level.getEntity(id) instanceof ArmedNpcEntity npc
                        && (npc.faction() == Faction.SERPENTS_HAND || npc.faction() == Faction.GOC)
                        && npc.blockPosition().distSqr(site.cassie) <= 16) {
                    site.cassieHack = Math.min(120, site.cassieHack + 1);
                }
            }
            int after = site.cassieHackStage();
            for (int stage = before + 1; stage <= after; stage++) onHackStage(level, site, stage);
        }

        if (site.hasUpgrade(Upgrade.MTF_CONTRACT) && !raid.mtfSent && now - raid.arriveTick >= MTF_DELAY) {
            raid.mtfSent = true;
            spawnMtf(level, site, 4, 6000);
            Cassie.broadcast(site, Component.literal("Mobile Task Force units have entered the facility.").withStyle(ChatFormatting.BLUE), true);
        }

        if (raid.raiders.isEmpty() || now - raid.startTick > 30000) end(level, site);
    }

    private static void spawnRaiders(ServerLevel level, Site site, RaidState raid) {
        int players = Math.max(1, Cassie.playersInSite(site).size());
        int perFaction = Math.min(12, 3 + site.scpCount / 2 + (players - 1) * 2);
        for (Faction f : raid.factions) {
            for (int i = 0; i < perFaction; i++) {
                ArmedNpcEntity e = ModEntities.ARMED_NPC.create(level, EntitySpawnReason.EVENT);
                if (e == null) continue;
                e.setup(f, site);
                BlockPos p = raid.spawn.offset(RANDOM.nextInt(7) - 3, 0, RANDOM.nextInt(7) - 3);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
                e.snapTo(p.getX() + 0.5, y, p.getZ() + 0.5, RANDOM.nextFloat() * 360, 0);
                level.addFreshEntity(e);
                raid.raiders.add(e.getUUID());
            }
        }
        raid.spawned = true;
        if (!site.cassieOperational()) site.log("Raiders arrived unannounced.");
    }

    private static void onHackStage(ServerLevel level, Site site, int stage) {
        switch (stage) {
            case 1 -> Cassie.alarm(site, "C.A.S.S.I.E. compromised. Automated defenses offline.");
            case 2 -> openCells(level, site, ObjectClass.SAFE, "Safe-class containment doors opened remotely!");
            case 3 -> openCells(level, site, ObjectClass.KETER, "Keter-class containment doors opened remotely!");
            case 4 -> openCells(level, site, null, "ALL containment doors opened remotely!");
            default -> {}
        }
    }

    /** Opens cells by class. With no SCP classes defined yet, cell type stands in: Light = Safe, Heavy = Keter. */
    private static void openCells(ServerLevel level, Site site, ObjectClass cls, String message) {
        for (Room cell : site.cells()) {
            boolean match = cls == null
                    || (cls == ObjectClass.SAFE && cell.type.endsWith("light_cell"))
                    || (cls == ObjectClass.KETER && cell.type.endsWith("heavy_cell"));
            if (match) DoorControl.setDoorsInBox(level, cell.box(), true);
        }
        Cassie.alarm(site, message);
    }

    // ---------------------------------------------------------------- objectives

    /** Where this raider should be heading, or null to just fight. */
    public static BlockPos objective(ArmedNpcEntity npc) {
        Site site = npc.site();
        if (site == null || site.raid == null || npc.faction() == Faction.MTF) return null;
        RaidState raid = site.raid;
        Faction f = npc.faction();
        int stage = raid.stage(f);
        Room entrance = site.firstEntrance();
        if (stage == 0) return entrance != null ? entrance.center() : site.mainframe;
        int escapeStage = f == Faction.SERPENTS_HAND ? 3 : 4;
        if (stage >= escapeStage) return raid.spawn;
        BlockPos target = switch (f) {
            case CHAOS_INSURGENCY -> switch (stage) {
                case 1 -> site.mainframeSabotaged ? null : site.mainframe;
                case 2 -> site.cassie != null && !site.cassieDisabled ? site.cassie : null;
                default -> nextRoomToHit(site, raid, "projectredacted:generator", "projectredacted:water_pump", "light_cell", "medium_cell", "heavy_cell");
            };
            case GOC -> switch (stage) {
                case 1 -> nextRoomToHit(site, raid, "projectredacted:generator");
                case 2 -> site.cassie != null && !site.cassieDisabled && site.cassieHack < 30 ? site.cassie : null;
                default -> nextOccupiedCell(site, raid);
            };
            case SERPENTS_HAND -> stage == 1
                    ? (site.cassie != null && !site.cassieDisabled && site.cassieHack < 120 ? site.cassie : null)
                    : nextOccupiedCell(site, raid);
            default -> null;
        };
        if (target == null) {
            // Nothing left for this stage: move on (eventually to escaping).
            raid.stage.put(f, stage + 1);
            return stage + 1 >= escapeStage ? raid.spawn : objective(npc);
        }
        return target;
    }

    private static BlockPos nextRoomToHit(Site site, RaidState raid, String... types) {
        for (Room r : site.rooms) {
            if (!r.built || raid.hitRooms.contains(r.number)) continue;
            for (String t : types) if (r.type.equals(t) || r.type.endsWith(t)) return r.center();
        }
        return null;
    }

    private static BlockPos nextOccupiedCell(Site site, RaidState raid) {
        for (Room cell : site.cells()) if (cell.scpCount > 0 && !raid.hitRooms.contains(cell.number)) return cell.center();
        return null;
    }

    /** Called when a raider reaches its objective. */
    public static void arrive(ServerLevel level, ArmedNpcEntity npc, BlockPos at) {
        Site site = npc.site();
        if (site == null || site.raid == null) return;
        RaidState raid = site.raid;
        Faction f = npc.faction();
        int stage = raid.stage(f);
        if (stage == 0) {
            raid.stage.put(f, 1);
            return;
        }
        Room room = site.roomAt(at);
        switch (f) {
            case CHAOS_INSURGENCY -> {
                if (stage == 1) {
                    if (!site.mainframeSabotaged) {
                        site.mainframeSabotaged = true;
                        Cassie.alarm(site, "The Mainframe has been sabotaged! Site operations halted. Repair it with a Repair Kit.");
                    }
                    raid.stage.put(f, 2);
                } else if (stage == 2) {
                    site.cassieDisabled = true;
                    Cassie.alarm(site, "C.A.S.S.I.E. has been disabled by the Chaos Insurgency.");
                    raid.stage.put(f, 3);
                } else if (stage == 3 && room != null) {
                    raid.hitRooms.add(room.number);
                    room.integrity = Math.max(0, room.integrity - 60);
                    capture(level, site, raid, room, npc);
                    if (raid.captured >= 1 + Cassie.playersInSite(site).size()) raid.stage.put(f, 4);
                } else if (stage >= 4) {
                    escape(npc, raid);
                }
            }
            case GOC -> {
                if (stage == 1 && room != null) {
                    raid.hitRooms.add(room.number);
                    room.integrity = 0;
                    Cassie.alarm(site, "Power failure in " + room.displayName() + ". The GOC has cut the power.");
                } else if (stage == 3 && room != null) {
                    raid.hitRooms.add(room.number);
                    DoorControl.setDoorsInBox(level, room.box(), true);
                } else if (stage >= 4) {
                    escape(npc, raid);
                }
            }
            case SERPENTS_HAND -> {
                if (stage == 2 && room != null) {
                    raid.hitRooms.add(room.number);
                    DoorControl.setDoorsInBox(level, room.box(), true);
                    takeScpItems(level, room, npc);
                    for (Entity e : level.getEntitiesOfClass(Entity.class, room.box().inflate(2), x -> x.getType().is(ModTags.SCP_ENTITIES))) {
                        ScpDefinition def = ScpApi.forEntity(e.getType());
                        if (def != null && def.followsHand) {
                            e.discard();
                            raid.captured++;
                            Cassie.alarm(site, def.number + " has left with the Serpent's Hand.");
                        }
                    }
                } else if (stage >= 3) {
                    escape(npc, raid);
                }
            }
            default -> {}
        }
    }

    private static void capture(ServerLevel level, Site site, RaidState raid, Room room, ArmedNpcEntity npc) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, room.box().inflate(2), x -> x.getType().is(ModTags.SCP_ENTITIES))) {
            ScpDefinition def = ScpApi.forEntity(e.getType());
            e.discard();
            raid.captured++;
            Cassie.alarm(site, (def != null ? def.number : e.getName().getString()) + " has been captured by the Chaos Insurgency!");
        }
        int before = npc.carried.size();
        takeScpItems(level, room, npc);
        raid.captured += npc.carried.size() - before;
    }

    private static void takeScpItems(ServerLevel level, Room room, ArmedNpcEntity npc) {
        for (BlockPos p : BlockPos.betweenClosed(room.x1, room.y1, room.z1, room.x2, room.y2, room.z2)) {
            if (level.getBlockEntity(p) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (s.is(ModTags.SCP_ITEMS)) {
                        npc.carried.add(s.copy());
                        c.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    private static void escape(ArmedNpcEntity npc, RaidState raid) {
        if (npc.blockPosition().distSqr(raid.spawn) <= 16) {
            raid.raiders.remove(npc.getUUID());
            npc.discard();
        }
    }

    // ---------------------------------------------------------------- after

    private static void end(ServerLevel level, Site site) {
        RaidState raid = site.raid;
        for (UUID id : raid.raiders) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        site.raid = null;
        site.raidCooldownUntil = level.getGameTime() + 48000 + RANDOM.nextInt(24000);
        String msg = "The raid is over." + (raid.captured > 0 ? " " + raid.captured + " SCP(s) were taken." : "")
                + " Damaged rooms need repairs.";
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.YELLOW), false);
        if (site.hasUpgrade(Upgrade.MTF_CONTRACT)) recontain(level, site, false);
    }

    /** MTF bring escaped SCPs back to their cells. Used after raids and when called in after a breach. */
    public static int recontain(ServerLevel level, Site site, boolean called) {
        int moved = 0;
        for (Map.Entry<UUID, Integer> e : new ArrayList<>(site.scpHomes.entrySet())) {
            Entity scp = level.getEntity(e.getKey());
            Room cell = site.room(e.getValue());
            if (scp == null || cell == null) {
                if (scp == null && cell == null) site.scpHomes.remove(e.getKey());
                continue;
            }
            if (!scp.isAlive() || cell.contains(scp.blockPosition())) continue;
            BlockPos c = cell.center();
            scp.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            DoorControl.setDoorsInBox(level, cell.box(), false);
            moved++;
        }
        // Loose SCP items lying around the site go back into a cell locker.
        AABB area = claimBox(site, level);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, i -> i.getItem().is(ModTags.SCP_ITEMS))) {
            if (storeInCell(level, site, item.getItem().copy())) {
                item.discard();
                moved++;
            }
        }
        spawnMtf(level, site, called ? 3 : 2, 1200);
        String msg = moved > 0 ? "Mobile Task Force recontained " + moved + " anomal" + (moved == 1 ? "y." : "ies.") : "Mobile Task Force sweep complete. Nothing to recontain.";
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.BLUE), false);
        return moved;
    }

    /** Terminal button: call the MTF after a breach. */
    public static String callMtf(ServerLevel level, Site site) {
        if (!site.hasUpgrade(Upgrade.MTF_CONTRACT)) return "You need the Mobile Task Force Contract upgrade.";
        long now = level.getGameTime();
        long ready = MTF_CALL_COOLDOWN.getOrDefault(site.id, 0L);
        if (now < ready) return "The MTF are still regrouping (" + (ready - now) / 20 + "s).";
        MTF_CALL_COOLDOWN.put(site.id, now + 6000);
        recontain(level, site, true);
        return null;
    }

    private static boolean storeInCell(ServerLevel level, Site site, ItemStack stack) {
        for (Room cell : site.cells()) {
            for (BlockPos p : BlockPos.betweenClosed(cell.x1, cell.y1, cell.z1, cell.x2, cell.y2, cell.z2)) {
                if (level.getBlockEntity(p) instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize(); i++) {
                        if (c.getItem(i).isEmpty()) {
                            c.setItem(i, stack);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static AABB claimBox(Site site, ServerLevel level) {
        int minX = site.minChunkX() << 4, minZ = site.minChunkZ() << 4, size = site.claimChunks() << 4;
        return new AABB(minX, level.getMinY(), minZ, minX + size, level.getMaxY(), minZ + size);
    }

    private static void spawnMtf(ServerLevel level, Site site, int count, int lifetime) {
        Room entrance = site.firstEntrance();
        BlockPos at = entrance != null ? entrance.center() : site.mainframe.above();
        for (int i = 0; i < count; i++) {
            ArmedNpcEntity e = ModEntities.ARMED_NPC.create(level, EntitySpawnReason.EVENT);
            if (e == null) continue;
            e.setup(Faction.MTF, site);
            e.expireTick = level.getGameTime() + lifetime;
            e.snapTo(at.getX() + 0.5 + RANDOM.nextInt(3) - 1, at.getY(), at.getZ() + 0.5 + RANDOM.nextInt(3) - 1, 0, 0);
            level.addFreshEntity(e);
        }
    }

    private RaidManager() {}
}
