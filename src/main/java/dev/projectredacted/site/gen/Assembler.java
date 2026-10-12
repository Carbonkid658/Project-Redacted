package dev.projectredacted.site.gen;

import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.SiteType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * Grows a facility from parts (design §5). Takes open doorways, picks parts, tries each rotation and keeps the
 * first that fits inside the claim without overlapping anything, on a storey the site type allows. Seeded, so
 * the same seed gives the same plan. Pure logic: the world only comes in through {@link Input}'s callbacks.
 */
public final class Assembler {
    public static class Input {
        public PartKit kit;
        public SiteType type = SiteType.SURFACE_FLAT;
        /** Where parts may go: the claim minus its margin, and the height limits. */
        public Box bounds;
        /** Floor height of storey 0 (the Director's Office storey). */
        public int baseFloor;
        /** Boxes already taken by rooms and parts. */
        public final List<Box> occupied = new ArrayList<>();
        /** Open doorways of the existing facility, in world coordinates. */
        public final List<SitePart.Connector> doorways = new ArrayList<>();
        /** Player-built blocks a box would replace. */
        public ToIntFunction<Box> overwrites = b -> 0;
        /** Whether a surface exit at this spot faces open air. */
        public BiPredicate<BlockPos, Direction> exitOk = (p, d) -> true;
        public long seed;
        public int budget = 25000;
        /** Try doorways nearest this spot first ("Near me"). */
        public BlockPos near;
    }

    /** What to place: a room type (optionally one size) or the Entrance, on a storey (null = any allowed). */
    public record Goal(String roomType, boolean entrance, Integer storey, SitePart only) {
        boolean met(SitePart p) {
            if (only != null) return p == only;
            if (entrance) return p.kind == SitePart.Kind.ENTRANCE;
            return p.kind != SitePart.Kind.ENTRANCE && p.roomTypes().contains(roomType);
        }

        String label() {
            if (entrance) return "an Entrance";
            var t = RoomTypes.get(roomType);
            return t == null ? roomType : "a " + t.name;
        }
    }

    private record Open(int part, int conn, int doorway, SitePart.Connector c) {}

    private final Input in;
    private final Random random;
    private final List<SitePlan.PlanPart> placed = new ArrayList<>();
    private final List<Open> open = new ArrayList<>();
    private final Set<Integer> usedDoorways = new HashSet<>();
    private int budget;
    /** Why the last part didn't fit, for error messages. */
    private String reject = "no Director's Office part";

    private Assembler(Input in, long seed) {
        this.in = in;
        this.random = new Random(seed);
        this.budget = in.budget;
        for (int i = 0; i < in.doorways.size(); i++) open.add(new Open(-1, -1, i, in.doorways.get(i)));
    }

    // ================================================================ entry points

    /** The Level 0 facility: Director's Office around the Mainframe, then a Barracks and an Entrance (§3.3). */
    public static SitePlan level0(Input in, BlockPos mainframe, int officeFloor) {
        SitePlan best = null;
        String error = "No room in the claim for the Director's Office.";
        int successes = 0;
        for (int attempt = 0; attempt < 8 && successes < 3; attempt++) {
            long seed = in.seed + attempt * 0x9E3779B97F4A7C15L;
            Assembler a = new Assembler(in, seed);
            if (!a.start(mainframe, officeFloor)) {
                error = "No room in the claim for the Director's Office (" + a.reject + ").";
                continue;
            }
            if (!a.attach(new Goal(RoomTypes.BARRACKS, false, 0, null), 3)) {
                error = "No room left in the claim for a Barracks. Upgrade the facility level for more space.";
                continue;
            }
            int surface = SiteTypeRules.surfaceStorey(in.type);
            if (!a.attach(new Goal(null, true, surface, null), surface + 4)) {
                error = in.type == SiteType.UNDERGROUND ? "Couldn't reach the surface for the Entrance from here." : "No room left in the claim for the Entrance.";
                continue;
            }
            SitePlan plan = a.result(seed);
            successes++;
            if (best == null || plan.overwrites < best.overwrites) best = plan;
            if (best.overwrites == 0) break;
        }
        if (best != null) return best;
        SitePlan failed = new SitePlan();
        failed.error = error;
        return failed;
    }

    /** One spot for a room attached to the existing facility (§8.1). */
    public static SitePlan addRoom(Input in, Goal goal) {
        List<SitePlan> spots = spots(in, goal, 1);
        if (!spots.isEmpty()) return spots.get(0);
        SitePlan failed = new SitePlan();
        failed.error = "No free space in the claim for " + goal.label() + ".";
        return failed;
    }

    /** Up to {@code max} different spots for a room, one per doorway it can grow from. */
    public static List<SitePlan> spots(Input in, Goal goal, int max) {
        List<SitePlan> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < in.doorways.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(in.seed));
        if (in.near != null) order.sort(Comparator.comparingDouble(i -> in.doorways.get(i).pos().distSqr(in.near)));
        for (int i : order) {
            if (out.size() >= max) break;
            Assembler a = new Assembler(in, in.seed ^ (i * 0x632BE59BD9B4E019L));
            a.budget = Math.max(800, in.budget / Math.max(1, Math.min(order.size(), 12)));
            Open start = a.open.get(i);
            if (!a.chain(start, goal, goal.storey() != null ? 5 : 3)) continue;
            SitePlan plan = a.result(in.seed);
            if (seen.add(plan.key())) out.add(plan);
        }
        return out;
    }

    // ================================================================ search

    private boolean start(BlockPos center, int floor) {
        List<SitePart> offices = in.kit.forRoom(RoomTypes.DIRECTORS_OFFICE);
        for (SitePart office : weighted(offices)) {
            for (int rot : rotations()) {
                Box atZero = office.box(BlockPos.ZERO, rot);
                BlockPos min = new BlockPos(center.getX() - (atZero.width() - 1) / 2, floor, center.getZ() - (atZero.length() - 1) / 2);
                BlockPos origin = office.originFor(min, rot);
                if (fits(office, origin, rot, 0) == null) continue;
                placed.add(new SitePlan.PlanPart(office, origin, rot, 0));
                addOpens(placed.size() - 1);
                return true;
            }
        }
        return false;
    }

    /** Attaches the goal to any open doorway, through up to {@code depth} hallways or stairwells. */
    private boolean attach(Goal goal, int depth) {
        List<Open> starts = new ArrayList<>(open);
        Collections.shuffle(starts, random);
        for (Open o : starts) {
            if (budget <= 0) return false;
            if (!open.contains(o)) continue;
            if (chain(o, goal, depth)) return true;
        }
        return false;
    }

    private boolean chain(Open from, Goal goal, int depth) {
        int storey = storeyOf(from.c);
        Integer target = goal.storey();
        int delta = target == null ? 0 : target - storey;
        if (Math.abs(delta) > depth) return false;
        for (SitePart p : candidates(goal, depth, delta, from.c)) {
            for (int rot : rotations()) {
                for (int ci = 0; ci < p.connectors.size(); ci++) {
                    if (--budget <= 0) return false;
                    SitePart.Connector local = p.worldConnector(ci, BlockPos.ZERO, rot);
                    if (!from.c.joins(local)) continue;
                    if (local.facing() != from.c.facing().getOpposite()) continue;
                    if (local.vertical() && local.top() != from.c.top()) continue;
                    BlockPos target2 = from.c.pos().relative(from.c.facing());
                    BlockPos origin = target2.subtract(local.pos());
                    Integer partStorey = fits(p, origin, rot, -1);
                    if (partStorey == null) continue;
                    if (!p.kind.equals(SitePart.Kind.HALLWAY) && !p.kind.equals(SitePart.Kind.STAIRWELL) && !goal.met(p)) continue;
                    if (goal.met(p) && target != null && partStorey != target.intValue()) continue;
                    SitePlan.PlanPart pp = new SitePlan.PlanPart(p, origin, rot, partStorey);
                    pp.used = 1 << ci;
                    int mark = open.size();
                    open.remove(from);
                    placed.add(pp);
                    int idx = placed.size() - 1;
                    addOpens(idx);
                    if (from.part >= 0) placed.get(from.part).used |= 1 << from.conn;
                    else usedDoorways.add(from.doorway);
                    if (goal.met(p)) return true;
                    if (depth > 0) {
                        List<Open> next = new ArrayList<>();
                        for (Open o : open) if (o.part == idx) next.add(o);
                        sortForStorey(next, delta);
                        for (Open o : next) if (chain(o, goal, depth - 1)) return true;
                    }
                    // undo
                    open.removeIf(o -> o.part == idx);
                    placed.remove(idx);
                    if (from.part >= 0) placed.get(from.part).used &= ~(1 << from.conn);
                    else usedDoorways.remove(from.doorway);
                    open.add(Math.min(mark - 1, open.size()), from);
                    if (budget <= 0) return false;
                }
            }
        }
        return false;
    }

    private List<SitePart> candidates(Goal goal, int depth, int delta, SitePart.Connector from) {
        List<SitePart> out = new ArrayList<>();
        if (delta == 0) {
            if (goal.only() != null) out.add(goal.only());
            else out.addAll(weighted(goal.entrance() ? in.kit.ofKind(SitePart.Kind.ENTRANCE) : in.kit.forRoom(goal.roomType())));
        }
        if (depth > 0) {
            List<SitePart> halls = weighted(in.kit.ofKind(SitePart.Kind.HALLWAY));
            List<SitePart> stairs = weighted(in.kit.ofKind(SitePart.Kind.STAIRWELL));
            if (delta != 0 || from.vertical()) {
                out.addAll(stairs);
                if (!from.vertical()) out.addAll(halls);
            } else {
                out.addAll(halls);
            }
        }
        return out;
    }

    /** Stair connectors first when changing storey, horizontal ones first otherwise. */
    private void sortForStorey(List<Open> next, int delta) {
        Collections.shuffle(next, random);
        next.sort(Comparator.comparingInt(o -> {
            if (delta > 0) return o.c.type().equals(SitePart.STAIR_UP) ? 0 : o.c.vertical() ? 2 : 1;
            if (delta < 0) return o.c.type().equals(SitePart.STAIR_DOWN) ? 0 : o.c.vertical() ? 2 : 1;
            return o.c.vertical() ? 1 : 0;
        }));
    }

    /** The storey a part sits on if it fits here, else null. */
    private Integer fits(SitePart p, BlockPos origin, int rot, int forcedStorey) {
        Box box = p.box(origin, rot);
        Box b = in.bounds;
        if (box.x1() < b.x1() || box.x2() > b.x2() || box.z1() < b.z1() || box.z2() > b.z2() || box.y1() < b.y1() || box.y2() > b.y2()) {
            reject = "outside the claim: " + box + " vs " + b;
            return null;
        }
        int storey = forcedStorey >= 0 ? forcedStorey : Math.floorDiv(box.y1() - in.baseFloor + SitePart.STOREY / 2, SitePart.STOREY);
        if (!SiteTypeRules.allowed(in.type, p, storey, p.storeys())) {
            reject = "storey " + storey + " not allowed";
            return null;
        }
        for (Box o : in.occupied) if (o.intersects(box)) {
            reject = "overlaps " + o;
            return null;
        }
        for (SitePlan.PlanPart o : placed) if (o.box.intersects(box)) {
            reject = "overlaps " + o.part.id;
            return null;
        }
        for (int i = 0; i < p.connectors.size(); i++) {
            SitePart.Connector c = p.worldConnector(i, origin, rot);
            if (c.type().equals(SitePart.SURFACE_EXIT) && !in.exitOk.test(c.pos(), c.facing())) {
                reject = "entrance would face a wall";
                return null;
            }
        }
        return storey;
    }

    private int storeyOf(SitePart.Connector c) {
        int floor = c.vertical() ? (c.facing() == Direction.UP ? c.pos().getY() + 1 : c.pos().getY() - SitePart.STOREY) : c.pos().getY() - 1;
        return Math.floorDiv(floor - in.baseFloor + SitePart.STOREY / 2, SitePart.STOREY);
    }

    private void addOpens(int idx) {
        SitePlan.PlanPart pp = placed.get(idx);
        for (int i = 0; i < pp.part.connectors.size(); i++) {
            if ((pp.used & (1 << i)) != 0) continue;
            SitePart.Connector c = pp.connector(i);
            if (c.type().equals(SitePart.SURFACE_EXIT)) continue;
            open.add(new Open(idx, i, -1, c));
        }
    }

    private int[] rotations() {
        int s = random.nextInt(4);
        return new int[]{s, (s + 1) & 3, (s + 2) & 3, (s + 3) & 3};
    }

    /** Parts in a random order where heavier ones tend to come first. */
    private List<SitePart> weighted(List<SitePart> parts) {
        List<SitePart> pool = new ArrayList<>(parts), out = new ArrayList<>();
        while (!pool.isEmpty()) {
            int total = 0;
            for (SitePart p : pool) total += Math.max(1, p.weight);
            int roll = random.nextInt(total);
            for (Iterator<SitePart> it = pool.iterator(); it.hasNext(); ) {
                SitePart p = it.next();
                roll -= Math.max(1, p.weight);
                if (roll < 0) {
                    out.add(p);
                    it.remove();
                    break;
                }
            }
        }
        return out;
    }

    private SitePlan result(long seed) {
        SitePlan plan = new SitePlan();
        plan.seed = seed;
        plan.parts.addAll(placed);
        plan.openedDoorways.addAll(usedDoorways);
        for (SitePlan.PlanPart p : placed) plan.overwrites += in.overwrites.applyAsInt(p.box);
        return plan;
    }
}
