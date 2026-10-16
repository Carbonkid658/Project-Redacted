package dev.projectredacted.staff;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteLogic;
import dev.projectredacted.util.Time;
import dev.projectredacted.room.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.*;

/**
 * Where a staff member should be right now: following someone, getting treated, sleeping, eating, or at the
 * post written on their clipboard (or their role's default post).
 */
public class StaffWorkGoal extends Goal {
    private static final Map<String, List<BlockPos>> BEDS = new HashMap<>();
    private static final Map<String, Long> BEDS_AT = new HashMap<>();

    private final StaffEntity mob;
    private final Random random = new Random();
    private BlockPos target;
    private int recheck;

    public StaffWorkGoal(StaffEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !mob.isSurvivor() && mob.getTarget() == null && mob.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        if (mob.isSleeping()) mob.stopSleeping();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        if (--recheck > 0) return;
        recheck = 20;
        Site site = mob.site();
        StaffRecord r = site == null ? null : site.staff.get(mob.getUUID());
        if (r == null) return;
        ServerLevel level = (ServerLevel) mob.level();
        boolean night = Time.night(level);

        // Field Agents heading out on an expedition walk to the site entrance and leave (§4.5).
        Expedition trip = r.role == StaffRole.FIELD_AGENT ? Expeditions.onTrip(site, mob.getUUID()) : null;
        if (trip != null && trip.leaving.contains(mob.getUUID())) {
            wake();
            BlockPos gate = Expeditions.gate(site);
            if (mob.blockPosition().distSqr(gate) <= 9) Expeditions.leftSite(mob, site);
            else go(gate, 1.1);
            return;
        }

        // Following a player (security) or researcher (D-Class).
        if (r.assignment.kind == Assignment.Kind.FOLLOW && r.assignment.leader != null) {
            Entity leader = level.getEntity(r.assignment.leader);
            if (leader == null) leader = level.getPlayerByUUID(r.assignment.leader);
            if (leader != null) {
                wake();
                if (mob.distanceToSqr(leader) > 9) mob.getNavigation().moveTo(leader, 1.1);
                return;
            }
        }

        // Hurt staff head to medical, if there's any.
        if (mob.getHealth() < mob.getMaxHealth() * 0.5) {
            Room med = nearestWorking(site, "projectredacted:medbay", "projectredacted:waiting_room", "projectredacted:checkup_room");
            if (med != null) {
                wake();
                goInto(level, site, med, null, 1.0);
                return;
            }
        }

        if (night && r.role != StaffRole.SECURITY) {
            BlockPos bed = bed(level, site, r);
            if (bed != null) {
                if (mob.blockPosition().distSqr(bed) <= 4) {
                    if (!mob.isSleeping()) mob.startSleeping(bed);
                } else {
                    go(bed, 0.9);
                }
                return;
            }
        }
        wake();

        long t = Time.dayTime(level);
        boolean mealtime = t < 2000 || (t >= 5000 && t < 7000) || (t >= 10000 && t < 12000);
        if (mealtime && r.hunger < 70 && r.role != StaffRole.DCLASS) {
            Room caf = nearestWorking(site, "projectredacted:cafeteria");
            if (caf != null) {
                goInto(level, site, caf, null, 1.0);
                return;
            }
        }

        Post post = post(site, r);
        if (post == null) return;
        if (post.room() == null) {
            if (mob.blockPosition().distSqr(post.spot()) > 25) go(post.spot(), 1.0);
            return;
        }
        goInto(level, site, post.room(), post, 1.0);
    }

    /** Where a staff member works: a room (null for a plain spot), and optionally a fixed spot and facing in it. */
    private record Post(Room room, BlockPos spot, Direction face) {}

    /**
     * Into a room the proper way (NPC revamp §4.2): to its Room Entrance Marker first, then to a work station
     * inside (or {@code post}'s fixed spot). Rooms without a marker are walked into directly.
     */
    private void goInto(ServerLevel level, Site site, Room room, Post post, double speed) {
        BlockPos spot;
        Stations.Station station = null;
        if (post != null && post.spot() != null) {
            spot = post.spot();
        } else {
            station = Stations.claim(level, site, room, mob.getUUID());
            spot = station != null ? station.stand() : room.center();
        }
        Stations.release(site, mob.getUUID(), room.number);
        boolean inside = room.contains(mob.blockPosition());
        if (!inside && room.entrance != null && !spot.equals(room.entrance)) {
            BlockPos door = room.outsideDoor();
            if (mob.blockPosition().distSqr(door) > 4) {
                go(door, speed);
                return;
            }
        }
        if (mob.blockPosition().distSqr(spot) > 2) {
            go(spot, speed);
            return;
        }
        // At the spot: face out of the doorway, or toward the station.
        if (post != null && post.face() != null) {
            BlockPos look = spot.relative(post.face(), 4);
            mob.getLookControl().setLookAt(look.getX() + 0.5, mob.getEyeY(), look.getZ() + 0.5);
        } else if (station != null && station.block() != null) {
            mob.getLookControl().setLookAt(station.block().getX() + 0.5, station.block().getY() + 0.5, station.block().getZ() + 0.5);
        }
    }

    private void wake() {
        if (mob.isSleeping()) mob.stopSleeping();
    }

    private void go(BlockPos pos, double speed) {
        if (target != null && target.equals(pos) && !mob.getNavigation().isDone()) return;
        target = pos;
        mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    private BlockPos bed(ServerLevel level, Site site, StaffRecord r) {
        boolean prison = r.role == StaffRole.DCLASS;
        String key = site.id + (prison ? ":prison" : ":housing");
        long now = level.getGameTime();
        if (now - BEDS_AT.getOrDefault(key, -10000L) > 600) {
            BEDS.put(key, SiteLogic.bedPositions(level, site, prison));
            BEDS_AT.put(key, now);
        }
        List<BlockPos> beds = BEDS.get(key);
        int index = r.bedIndex;
        if (prison) {
            index = 0;
            for (StaffRecord s : site.staff.values()) {
                if (s == r) break;
                if (s.role == StaffRole.DCLASS) index++;
            }
        }
        return index >= 0 && index < beds.size() ? beds.get(index) : null;
    }

    /** The clipboard target, or a sensible default post for the role. */
    private Post post(Site site, StaffRecord r) {
        Assignment a = r.assignment;
        Room room = a.kind == Assignment.Kind.NONE || a.kind == Assignment.Kind.FOLLOW ? null : site.room(a.room);
        if (room != null) {
            return switch (a.kind) {
                case SCP -> {
                    if (r.role == StaffRole.RESEARCHER) {
                        // The Observation Room linked to this cell (v4 §5.5), else the nearest one.
                        Room obs = null;
                        for (Room o : site.rooms) if (o.watches == room.number && o.working()) obs = o;
                        if (obs == null) obs = nearestTo(site, room.center(), "projectredacted:observation_room");
                        if (obs != null && obs.working()) yield new Post(obs, null, null);
                    }
                    if (r.role == StaffRole.SECURITY) yield guard(room);
                    yield new Post(null, room.outsideDoor(), null);
                }
                case CHECKPOINT -> r.role == StaffRole.SECURITY ? guard(room) : new Post(room, null, null);
                default -> new Post(room, null, null);
            };
        }
        Room fallback = switch (r.role) {
            case RESEARCHER -> nearestWorking(site, "projectredacted:lab", "projectredacted:researcher_office");
            case SECURITY -> firstOf(site, "projectredacted:hallway_checkpoint", "projectredacted:transition_checkpoint", "projectredacted:entrance");
            case ENGINEER -> worstRoom(site);
            case JANITOR -> randomRoom(site);
            case ADMINISTRATION -> firstOf(site, "projectredacted:admin_office", "projectredacted:directors_office");
            case MEDICAL -> firstOf(site, "projectredacted:medbay", "projectredacted:checkup_room");
            case DCLASS -> firstOf(site, "projectredacted:prison");
            case FIELD_AGENT -> firstOf(site, "projectredacted:field_operations", "projectredacted:entrance");
        };
        if (fallback == null) fallback = firstOf(site, "projectredacted:directors_office");
        if (fallback == null) return null;
        RoomType t = fallback.typeDef();
        if (r.role == StaffRole.SECURITY && t != null && t.isCheckpoint()) return guard(fallback);
        return new Post(fallback, null, null);
    }

    /** Security at a checkpoint or SCP cell stands on the entrance marker, facing out (§4.2). */
    private static Post guard(Room room) {
        if (room.entrance == null || room.entranceSide < 0) return new Post(null, room.outsideDoor(), null);
        return new Post(room, room.entrance, room.doorSide());
    }

    private Room nearestWorking(Site site, String... types) {
        Room best = null;
        double bestD = Double.MAX_VALUE;
        for (Room room : site.rooms) {
            if (!room.working()) continue;
            for (String t : types) {
                if (!room.type.equals(t)) continue;
                double d = room.center().distSqr(mob.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    best = room;
                }
            }
        }
        return best;
    }

    private Room nearestTo(Site site, BlockPos pos, String type) {
        Room best = null;
        double bestD = Double.MAX_VALUE;
        for (Room room : site.rooms(type)) {
            double d = room.center().distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = room;
            }
        }
        return best;
    }

    private Room firstOf(Site site, String... types) {
        for (String t : types) for (Room room : site.rooms(t)) if (room.built) return room;
        return null;
    }

    private Room worstRoom(Site site) {
        Room worst = null;
        for (Room room : site.rooms) if (room.built && room.integrity < 100 && (worst == null || room.integrity < worst.integrity)) worst = room;
        if (worst != null) return worst;
        return firstOf(site, "projectredacted:generator", "projectredacted:water_pump", "projectredacted:storage_room");
    }

    private Room randomRoom(Site site) {
        List<Room> built = new ArrayList<>();
        for (Room room : site.rooms) {
            RoomType t = room.typeDef();
            if (room.built && t != null && !t.isContainment()) built.add(room);
        }
        if (built.isEmpty()) return null;
        return built.get((int) ((mob.level().getGameTime() / 1200 + mob.getId()) % built.size()));
    }
}
