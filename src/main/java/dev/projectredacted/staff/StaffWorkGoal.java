package dev.projectredacted.staff;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteLogic;
import dev.projectredacted.util.Time;
import net.minecraft.core.BlockPos;
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
    private int idleWander;

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
                go(med.center(), 1.0);
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
                go(caf.center(), 1.0);
                return;
            }
        }

        BlockPos post = post(site, r);
        if (post == null) return;
        if (mob.blockPosition().distSqr(post) > 25) {
            go(post, 1.0);
        } else if (--idleWander <= 0) {
            idleWander = 3 + random.nextInt(5);
            go(post.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), 0.6);
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
    private BlockPos post(Site site, StaffRecord r) {
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
                        yield obs != null && obs.working() ? obs.center() : room.outsideDoor();
                    }
                    yield room.outsideDoor();
                }
                case ENTRANCE -> room.center();
                default -> room.center();
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
        };
        if (fallback == null) fallback = firstOf(site, "projectredacted:directors_office");
        return fallback == null ? null : fallback.center();
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
