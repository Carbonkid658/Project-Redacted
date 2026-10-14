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
        /** For entries of {@link #occupied} that are parts with a real shape (§5.7): the part, placed. */
        public final Map<Integer, Placed> shapes = new HashMap<>();
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
        /** Surface-Flat sites with their containment layer one storey down (D14). */
        public boolean basement;
        /** How areas join (v4 §4.1). */
        public Areas.Layout layout = Areas.Layout.HUB;
    }

    /** A part as placed: where its real shape is (§5.7). */
    public record Placed(SitePart part, BlockPos origin, int rot) {}

    /**
     * Whether two placed parts really overlap: their boxes meet, and so do their shapes (§5.7). Rooms with odd
     * shapes can nest into each other's empty corners.
     */
    public static boolean overlaps(SitePart a, BlockPos ao, int ar, Box abox, SitePart b, BlockPos bo, int br, Box bbox) {
        if (!abox.intersects(bbox)) return false;
        if ((a == null || a.solid == null) && (b == null || b.solid == null)) return true;
        int x1 = Math.max(abox.x1(), bbox.x1()), y1 = Math.max(abox.y1(), bbox.y1()), z1 = Math.max(abox.z1(), bbox.z1());
        int x2 = Math.min(abox.x2(), bbox.x2()), y2 = Math.min(abox.y2(), bbox.y2()), z2 = Math.min(abox.z2(), bbox.z2());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            m.set(x, y, z);
            if ((a == null || a.solidAtWorld(m, ao, ar)) && (b == null || b.solidAtWorld(m, bo, br))) return true;
        }
        return false;
    }

    /**
     * A part that must come on the way to the goal (v4 §3.1), in order: e.g. a checkpoint, then a main hall piece.
     * Parts placed after it get {@code after} as their role.
     */
    /** One part on the way to a goal; {@code minHalls} asks for a piece with at least that many hall ends (a branch). */
    public record Step(SitePart.Kind kind, String tag, String role, Integer storey, String after, int minHalls) {}

    /**
     * What to place: a room type (optionally one size), the Entrance or a part kind, on a storey (null = any
     * allowed), optionally through some {@link Step}s first.
     */
    public static final class Goal {
        private final String roomType;
        private final boolean entrance;
        private final Integer storey;
        private final SitePart only;
        /** Target a part kind (a hub, a spine piece) instead of a room, with this tag and at least minHalls hall openings. */
        public SitePart.Kind kind;
        public String tag;
        public int minHalls;
        /** The area ("living-2") the area parts go in; its kind picks the area's own pieces. */
        public String area;
        public final List<Step> via = new ArrayList<>();
        /** Role of the target part, and of in-between parts before the first step. */
        public String role = Areas.ROLE_ROOM, startRole = Areas.ROLE_LINK;

        public Goal(String roomType, boolean entrance, Integer storey, SitePart only) {
            this.roomType = roomType;
            this.entrance = entrance;
            this.storey = storey;
            this.only = only;
        }

        public static Goal of(SitePart.Kind kind, Integer storey, String role) {
            Goal g = new Goal(null, false, storey, null);
            g.kind = kind;
            g.role = role;
            return g;
        }

        public String roomType() {
            return roomType;
        }

        public boolean entrance() {
            return entrance;
        }

        public Integer storey() {
            return storey;
        }

        public SitePart only() {
            return only;
        }

        public Goal then(SitePart.Kind kind, String tag, String role, Integer storey, String after) {
            return then(kind, tag, role, storey, after, 0);
        }

        public Goal then(SitePart.Kind kind, String tag, String role, Integer storey, String after, int minHalls) {
            via.add(new Step(kind, tag, role, storey, after, minHalls));
            return this;
        }

        /** A layout piece (a hub, or a spine or tunnel branch) as the next step. */
        public Goal then(Goal piece, String after) {
            return then(piece.kind, piece.tag, piece.role, piece.storey(), after, piece.minHalls);
        }

        boolean met(SitePart p) {
            if (only != null) return p == only;
            if (kind != null) return p.kind == kind && (tag == null || p.tagged(tag)) && halls(p) >= minHalls;
            if (entrance) return p.kind == SitePart.Kind.ENTRANCE;
            return p.kind != SitePart.Kind.ENTRANCE && p.roomTypes().contains(roomType);
        }

        String kindArea() {
            return Areas.kindOf(area);
        }

        String label() {
            if (kind != null) return "a " + kind.label();
            if (entrance) return "an Entrance";
            var t = RoomTypes.get(roomType);
            return t == null ? roomType : "a " + t.name;
        }
    }

    private record Open(int part, int conn, int doorway, SitePart.Connector c) {}

    /** Entry parts (§5.5): a part that joins the halls and brings the goal's room in as its companion. */
    private final Map<SitePart, SitePart> entryFor = new HashMap<>();
    private Goal entryGoal;

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

    /**
     * The starting site (v4 §4.2): the Director's Office around the Mainframe, the Admin area's main hall from its
     * door with the Admin checkpoint facing out, the layout piece on that checkpoint, Containment (with a Light
     * Cell) and Living (with the Barracks) each behind their own checkpoint, and the Entrance on the layout piece.
     */
    public static SitePlan level0(Input in, BlockPos mainframe, int officeFloor) {
        SitePlan best = null;
        String error = "No room in the claim for the Director's Office.";
        int successes = 0;
        Set<String> layoutRoles = Set.of(Areas.ROLE_HUB, Areas.ROLE_SPINE, Areas.ROLE_TUNNEL);
        for (int attempt = 0; attempt < 10 && successes < 3; attempt++) {
            long seed = in.seed + attempt * 0x9E3779B97F4A7C15L;
            Assembler a = new Assembler(in, seed);
            if (!a.start(mainframe, officeFloor)) {
                error = "No room in the claim for the Director's Office (" + a.reject + ").";
                continue;
            }
            String admin = Areas.key(Areas.ADMIN, 1);
            a.placed.get(0).area = admin;
            Goal ag = Goal.of(SitePart.Kind.GATE, 0, Areas.ROLE_CHECKPOINT);
            ag.area = admin;
            ag.startRole = Areas.ROLE_MAIN_HALL;
            ag.then(SitePart.Kind.HALLWAY, SitePart.TAG_MAIN_HALL, Areas.ROLE_MAIN_HALL, 0, Areas.ROLE_MAIN_HALL);
            if (!a.attach(ag, 1, Set.of(Areas.ROLE_ROOM))) {
                error = "No room in the claim for the Admin area's hall and checkpoint.";
                continue;
            }
            // The Admin checkpoint was built from inside, so its readers face the other way round.
            for (SitePlan.PlanPart pp : a.placed) if (Areas.ROLE_CHECKPOINT.equals(pp.role)) pp.entryInside = true;
            // A four-way piece if the kit has one (it leaves the layout room to grow), else three-way.
            Goal lg = layoutPiece(in.layout, 0);
            lg.minHalls = 4;
            boolean laid = a.attach(lg, 0, Set.of(Areas.ROLE_CHECKPOINT));
            lg.minHalls = 3;
            if (!laid && !a.attach(lg, 0, Set.of(Areas.ROLE_CHECKPOINT)) && !a.attach(lg, 1, Set.of(Areas.ROLE_CHECKPOINT))) {
                error = "No room next to the Admin checkpoint for the " + in.layout.displayName.toLowerCase(Locale.ROOT) + ".";
                continue;
            }
            // The Entrance goes at the far end: the hub arm or spine/tunnel end furthest from the office.
            int surface = SiteTypeRules.surfaceStorey(in.type);
            Goal eg = new Goal(null, true, surface, null);
            eg.startRole = Areas.ROLE_LINK; // plain halls: spine and tunnel pieces have no doorways
            if (!a.attach(eg, surface + 4, layoutRoles, mainframe)) {
                error = in.type == SiteType.UNDERGROUND ? "Couldn't reach the surface for the Entrance from here." : "No room left in the claim for the Entrance.";
                continue;
            }
            int reach = in.layout == Areas.Layout.HUB ? 1 : 3;
            if (!a.attach(areaGoal(in.layout, Areas.key(Areas.CONTAINMENT, 1), "projectredacted:light_cell", 0), reach, layoutRoles)) {
                error = "No room left in the claim for the Containment area.";
                continue;
            }
            if (!a.attach(areaGoal(in.layout, Areas.key(Areas.LIVING, 1), RoomTypes.BARRACKS, 0), reach, layoutRoles)) {
                error = "No room left in the claim for the Living area. Upgrade the facility level for more space.";
                continue;
            }
            // Keep a free hub arm or spine/tunnel end, so later areas have somewhere to join.
            if (a.openLayoutEnds() == 0) {
                error = "No room left for the " + in.layout.displayName.toLowerCase(Locale.ROOT) + " to grow.";
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

    /** The piece areas join through (§4.1): a hub, or a spine or tunnel piece with a branch. */
    public static Goal layoutPiece(Areas.Layout layout, Integer storey) {
        Goal g = switch (layout) {
            case HUB -> Goal.of(SitePart.Kind.HUB, storey, Areas.ROLE_HUB);
            case SPINE -> Goal.of(SitePart.Kind.HALLWAY, storey, Areas.ROLE_SPINE);
            case SECTORS -> Goal.of(SitePart.Kind.HALLWAY, storey, Areas.ROLE_TUNNEL);
        };
        g.tag = layout == Areas.Layout.SPINE ? SitePart.TAG_SPINE : layout == Areas.Layout.SECTORS ? SitePart.TAG_TUNNEL : null;
        g.minHalls = 3;
        g.startRole = Areas.ROLE_LINK;
        return g;
    }

    /** The in-between pieces a layout grows with: spine pieces, tunnels, or plain halls off a hub. */
    public static String layoutFiller(Areas.Layout layout) {
        return switch (layout) {
            case HUB -> Areas.ROLE_LINK;
            case SPINE -> Areas.ROLE_SPINE;
            case SECTORS -> Areas.ROLE_TUNNEL;
        };
    }

    /** A new area off the layout: its checkpoint, a main hall piece, then the room (§3, §4.1). */
    public static Goal areaGoal(Areas.Layout layout, String area, String roomType, Integer storey) {
        Goal g = new Goal(roomType, false, storey, null);
        return areaGoal(layout, area, g, storey);
    }

    public static Goal areaGoal(Areas.Layout layout, String area, Goal g, Integer storey) {
        g.area = area;
        g.startRole = layoutFiller(layout);
        g.then(SitePart.Kind.GATE, null, Areas.ROLE_CHECKPOINT, storey, Areas.ROLE_MAIN_HALL);
        g.then(SitePart.Kind.HALLWAY, SitePart.TAG_MAIN_HALL, Areas.ROLE_MAIN_HALL, null, Areas.ROLE_MAIN_HALL);
        return g;
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
        return spots(in, goal, max, null, null, 0, goal.storey() != null ? 5 : 3);
    }

    /**
     * Spots growing only from the doorways in {@code starts} (null for all), trying those nearest
     * {@code toward} first (or {@link Input#near}), with {@code tries} doorways at most (0 for no limit).
     */
    public static List<SitePlan> spots(Input in, Goal goal, int max, Collection<Integer> starts, BlockPos toward, int tries, int depth) {
        List<SitePlan> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < in.doorways.size(); i++) if (starts == null || starts.contains(i)) order.add(i);
        Collections.shuffle(order, new Random(in.seed));
        BlockPos near = in.near != null ? in.near : toward;
        if (near != null) order.sort(Comparator.comparingDouble(i -> in.doorways.get(i).pos().distSqr(near)));
        if (tries > 0 && order.size() > tries) order = new ArrayList<>(order.subList(0, tries));
        int per = Math.max(goal.via.isEmpty() ? 800 : 3000, in.budget / Math.max(1, Math.min(order.size(), 12)));
        for (int i : order) {
            if (out.size() >= max) break;
            Assembler a = new Assembler(in, in.seed ^ (i * 0x632BE59BD9B4E019L));
            a.budget = per;
            Open start = a.open.get(i);
            if (!a.chain(start, goal, depth, 0, goal.startRole)) continue;
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
                SitePlan.PlanPart pp = new SitePlan.PlanPart(office, origin, rot, 0);
                pp.role = Areas.ROLE_ROOM;
                placed.add(pp);
                addOpens(placed.size() - 1);
                return true;
            }
        }
        return false;
    }

    /** Attaches the goal to an open doorway of a part with one of {@code fromRoles}, through up to {@code depth} in-between parts. */
    private boolean attach(Goal goal, int depth, Set<String> fromRoles) {
        return attach(goal, depth, fromRoles, null);
    }

    /** As above, trying the doorways furthest from {@code awayFrom} first. */
    private boolean attach(Goal goal, int depth, Set<String> fromRoles, BlockPos awayFrom) {
        List<Open> starts = new ArrayList<>();
        for (Open o : open) if (o.part >= 0 && (fromRoles == null || fromRoles.contains(placed.get(o.part).role))) starts.add(o);
        Collections.shuffle(starts, random);
        if (awayFrom != null) starts.sort(Comparator.comparingDouble(o -> -o.c.pos().distSqr(awayFrom)));
        for (Open o : starts) {
            if (budget <= 0) return false;
            if (!open.contains(o)) continue;
            if (chain(o, goal, depth, 0, goal.startRole)) return true;
        }
        return false;
    }

    /**
     * Places parts from {@code from} until the goal is met: the goal's steps in order (they don't use up
     * {@code depth}), with up to {@code depth} hallways or stairwells in between.
     */
    private boolean chain(Open from, Goal goal, int depth, int step, String fillerRole) {
        int storey = storeyOf(from.c);
        Step pending = step < goal.via.size() ? goal.via.get(step) : null;
        Integer target = pending != null && pending.storey() != null ? pending.storey() : goal.storey();
        int delta = target == null ? 0 : target - storey;
        if (Math.abs(delta) > depth) return false;
        for (SitePart p : candidates(goal, pending, depth, delta, from.c, fillerRole)) {
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
                    boolean isStep = pending != null && stepMet(pending, p, goal) && (pending.storey() == null || partStorey == pending.storey().intValue());
                    boolean isTarget = pending == null && (goal.met(p) || entryFor.containsKey(p)) && (target == null || partStorey == target.intValue());
                    boolean isFiller = depth > 0 && filler(p, goal, fillerRole);
                    if (!isStep && !isTarget && !isFiller) continue;
                    SitePlan.PlanPart pp = new SitePlan.PlanPart(p, origin, rot, partStorey);
                    pp.used = 1 << ci;
                    pp.entry = ci;
                    pp.role = isTarget ? goal.role : isStep ? pending.role() : p.kind == SitePart.Kind.STAIRWELL ? Areas.ROLE_LINK : fillerRole;
                    if (Areas.AREA_ROLES.contains(pp.role)) pp.area = goal.area;
                    int mark = open.size();
                    open.remove(from);
                    placed.add(pp);
                    int idx = placed.size() - 1;
                    addOpens(idx);
                    if (from.part >= 0) placed.get(from.part).used |= 1 << from.conn;
                    else usedDoorways.add(from.doorway);
                    if (isTarget) {
                        // Companions come along (§5.5); a missing required one means trying elsewhere.
                        SitePart forced = entryFor.get(p);
                        if (placeGroup(idx, forced, 3) && (forced == null || inGroup(idx, forced))) return true;
                        rollback(idx + 1);
                        pp.used = 1 << ci;
                    } else if (isStep || depth > 0) {
                        List<Open> next = new ArrayList<>();
                        for (Open o : open) if (o.part == idx) next.add(o);
                        sortForStorey(next, delta);
                        for (Open o : next) {
                            if (isStep ? chain(o, goal, depth, step + 1, pending.after()) : chain(o, goal, depth - 1, step, fillerRole)) return true;
                        }
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

    private List<SitePart> candidates(Goal goal, Step pending, int depth, int delta, SitePart.Connector from, String fillerRole) {
        List<SitePart> out = new ArrayList<>();
        String area = goal.kindArea();
        if (entryGoal != goal) {
            entryFor.clear();
            entryGoal = goal;
        }
        if (delta == 0) {
            if (pending != null) out.addAll(weighted(in.kit.pieces(pending.kind(), pending.tag(), area)));
            else if (goal.only() != null) out.add(goal.only());
            else if (goal.kind != null) out.addAll(weighted(in.kit.pieces(goal.kind, goal.tag, area)));
            else out.addAll(weighted(goal.entrance() ? in.kit.ofKind(SitePart.Kind.ENTRANCE) : in.kit.forRoom(goal.roomType())));
            // Rooms without a hall of their own come in through a companion that has one (§5.5).
            if (pending == null && goal.kind == null) {
                for (SitePart t : new ArrayList<>(out)) {
                    for (SitePart e : in.kit.entriesFor(t)) {
                        if (out.contains(e) || entryFor.containsKey(e)) continue;
                        entryFor.put(e, t);
                        out.add(e);
                    }
                }
            }
        }
        if (depth > 0) {
            List<SitePart> halls = weighted(in.kit.pieces(SitePart.Kind.HALLWAY, fillerTag(fillerRole), fillerTag(fillerRole) == null ? null : area));
            List<SitePart> stairs = weighted(in.kit.pieces(SitePart.Kind.STAIRWELL, null, null));
            if (delta != 0 || from.vertical()) {
                out.addAll(stairs);
                if (!from.vertical()) out.addAll(halls);
            } else {
                out.addAll(halls);
            }
        }
        return out;
    }

    /** The hallway tag in-between parts have in each role: an area's halls, the spine, tunnels, or none. */
    private static String fillerTag(String role) {
        return switch (role) {
            case Areas.ROLE_MAIN_HALL -> SitePart.TAG_MAIN_HALL;
            case Areas.ROLE_SIDE_HALL -> SitePart.TAG_SIDE_HALL;
            case Areas.ROLE_SPINE -> SitePart.TAG_SPINE;
            case Areas.ROLE_TUNNEL -> SitePart.TAG_TUNNEL;
            default -> null;
        };
    }

    private static boolean stepMet(Step s, SitePart p, Goal goal) {
        if (p.kind != s.kind() || (p.area != null && !p.area.equals(goal.kindArea())) || halls(p) < s.minHalls()) return false;
        return s.tag() == null ? s.kind() != SitePart.Kind.HALLWAY || p.tags.isEmpty() : p.tagged(s.tag());
    }

    private boolean filler(SitePart p, Goal goal, String role) {
        if (p.kind == SitePart.Kind.STAIRWELL) return true;
        if (p.kind != SitePart.Kind.HALLWAY) return false;
        String tag = fillerTag(role);
        if (tag == null) return p.tags.isEmpty() && p.area == null;
        return p.tagged(tag) && (p.area == null || p.area.equals(goal.kindArea()));
    }

    /** Open hall ends on the layout's pieces (hubs, spine, tunnels and the plain halls between them). */
    private int openLayoutEnds() {
        int n = 0;
        List<Box> boxes = new ArrayList<>();
        for (SitePlan.PlanPart pp : placed) boxes.add(pp.box);
        for (Open o : open) if (o.part >= 0 && o.c.type().equals(SitePart.HALL) && LAYOUT_ROLES.contains(placed.get(o.part).role) && clearAhead(in, o.c.pos(), o.c.facing(), 10, boxes)) n++;
        return n;
    }

    public static final Set<String> LAYOUT_ROLES = Set.of(Areas.ROLE_HUB, Areas.ROLE_SPINE, Areas.ROLE_TUNNEL, Areas.ROLE_LINK);

    static int halls(SitePart p) {
        int n = 0;
        for (SitePart.Connector c : p.connectors) if (c.type().equals(SitePart.HALL)) n++;
        return n;
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

    /**
     * Whether a hall end leads anywhere: the {@code len} blocks in front of it (five wide, a storey high) are inside
     * the claim and clear of the facility and of {@code extra}. A layout end that runs into a wall is no end.
     */
    public static boolean clearAhead(Input in, BlockPos pos, Direction facing, int len, Iterable<Box> extra) {
        Direction side = facing.getClockWise();
        BlockPos a = pos.relative(facing).relative(side, 2).below();
        BlockPos b = pos.relative(facing, len).relative(side, -2).above(3);
        Box box = Box.of(a, b);
        Box c = in.bounds;
        if (box.x1() < c.x1() || box.x2() > c.x2() || box.z1() < c.z1() || box.z2() > c.z2()) return false;
        for (Box o : in.occupied) if (o.intersects(box)) return false;
        for (Box o : extra) if (o.intersects(box)) return false;
        return true;
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
        if (!SiteTypeRules.allowed(in.type, p, storey, p.storeys(), in.basement)) {
            reject = "storey " + storey + " not allowed";
            return null;
        }
        for (int i = 0; i < in.occupied.size(); i++) {
            Box o = in.occupied.get(i);
            if (!o.intersects(box)) continue;
            Placed s = in.shapes.get(i);
            if (s != null || p.solid != null) {
                if (!overlaps(p, origin, rot, box, s == null ? null : s.part(), s == null ? null : s.origin(), s == null ? 0 : s.rot(), o)) continue;
            }
            reject = "overlaps " + o;
            return null;
        }
        for (SitePlan.PlanPart o : placed) {
            if (overlaps(p, origin, rot, box, o.part, o.origin, o.rot, o.box)) {
                reject = "overlaps " + o.part.id;
                return null;
            }
            for (int i = 0; i < o.part.connectors.size(); i++) {
                SitePart.Connector c = o.connector(i);
                if (c.type().equals(SitePart.SURFACE_EXIT) && SitePart.exitClearance(c).intersects(box)) {
                    reject = "in front of the Entrance";
                    return null;
                }
            }
        }
        for (int i = 0; i < p.connectors.size(); i++) {
            SitePart.Connector c = p.worldConnector(i, origin, rot);
            if (!c.type().equals(SitePart.SURFACE_EXIT)) continue;
            if (!in.exitOk.test(c.pos(), c.facing())) {
                reject = "entrance would face a wall";
                return null;
            }
            Box clear = SitePart.exitClearance(c);
            for (Box o : in.occupied) if (o.intersects(clear)) {
                reject = "entrance would face the facility";
                return null;
            }
            for (SitePlan.PlanPart o : placed) if (o.box.intersects(clear)) {
                reject = "entrance would face the facility";
                return null;
            }
        }
        return storey;
    }

    private int storeyOf(SitePart.Connector c) {
        int floor = c.vertical() ? (c.facing() == Direction.UP ? c.pos().getY() + 1 : c.pos().getY() - SitePart.STOREY) : c.pos().getY() - 1;
        return Math.floorDiv(floor - in.baseFloor + SitePart.STOREY / 2, SitePart.STOREY);
    }

    // ================================================================ companion rooms (§5.5, §5.6)

    /**
     * Places a companion on each link of the part at {@code idx}, joined jigsaw to jigsaw, and theirs in turn (up to
     * {@code depth} deep, for chains). A required link with nothing that fits fails the whole group; an optional
     * one is left unused, so it's walled up. {@code forced} is used for the first link that accepts it.
     */
    private boolean placeGroup(int idx, SitePart forced, int depth) {
        SitePlan.PlanPart main = placed.get(idx);
        for (int li = 0; li < main.part.connectors.size(); li++) {
            SitePart.Connector lc = main.part.connectors.get(li);
            if (!lc.isLink() || (main.used & (1 << li)) != 0) continue;
            SitePart.Link link = lc.link();
            if (link == null) continue;
            if (depth <= 0) {
                if (link.required()) return false;
                continue;
            }
            SitePart.Connector from = main.connector(li);
            List<SitePart> options = forced != null && link.accepts(forced) && forced.linksTo(main.part) ? List.of(forced) : weighted(in.kit.companions(main.part, link));
            boolean done = false;
            for (SitePart c : options) {
                if (done) break;
                for (int rot : rotations()) {
                    if (done) break;
                    for (int bi = 0; bi < c.connectors.size(); bi++) {
                        if (--budget <= 0) return false;
                        SitePart.Connector back = c.connectors.get(bi);
                        if (!back.isLink() || back.link() == null || !back.link().accepts(main.part)) continue;
                        SitePart.Connector local = c.worldConnector(bi, BlockPos.ZERO, rot);
                        if (local.facing() != from.facing().getOpposite()) continue;
                        BlockPos origin = from.pos().relative(from.facing()).subtract(local.pos());
                        Integer storey = fits(c, origin, rot, -1);
                        if (storey == null) continue;
                        SitePlan.PlanPart cp = new SitePlan.PlanPart(c, origin, rot, storey);
                        cp.used = 1 << bi;
                        cp.entry = bi;
                        cp.linkedFrom = idx;
                        cp.role = c.kind == SitePart.Kind.ROOM || c.kind == SitePart.Kind.ENTRANCE ? Areas.ROLE_ROOM : Areas.ROLE_LINK;
                        cp.area = main.area;
                        placed.add(cp);
                        int ci = placed.size() - 1;
                        addOpens(ci);
                        main.used |= 1 << li;
                        SitePart nextForced = c == forced ? null : forced;
                        if (placeGroup(ci, nextForced, depth - 1)) {
                            if (c == forced) forced = null;
                            done = true;
                            break;
                        }
                        rollback(ci);
                        main.used &= ~(1 << li);
                    }
                }
            }
            if (!done && link.required()) return false;
        }
        return true;
    }

    /** Whether {@code part} is among the parts placed from {@code idx} on (the group). */
    private boolean inGroup(int idx, SitePart part) {
        for (int i = idx; i < placed.size(); i++) if (placed.get(i).part == part) return true;
        return false;
    }

    /** Drops the parts placed from {@code idx} on. */
    private void rollback(int idx) {
        open.removeIf(o -> o.part >= idx);
        while (placed.size() > idx) placed.remove(placed.size() - 1);
    }

    private void addOpens(int idx) {
        SitePlan.PlanPart pp = placed.get(idx);
        for (int i = 0; i < pp.part.connectors.size(); i++) {
            if ((pp.used & (1 << i)) != 0) continue;
            SitePart.Connector c = pp.connector(i);
            if (c.type().equals(SitePart.SURFACE_EXIT) || c.isLink()) continue;
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
