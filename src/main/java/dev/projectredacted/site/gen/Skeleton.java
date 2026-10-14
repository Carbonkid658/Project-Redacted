package dev.projectredacted.site.gen;

import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteType;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.function.Predicate;

/**
 * Decides where in the facility a new room goes before the assembler places parts (site generation v4, §3.1).
 * A room only goes in an area of its own kind, and an area fills up before a new one of the same kind starts:
 * a free door slot on its halls, then one more side hall piece (or a new side hall), then one more main hall
 * piece, and only then a new area with its own checkpoint, joined by the site's layout (hub, spine or sectors).
 */
public final class Skeleton {
    /**
     * Up to {@code max} spots for a room. {@code anywhere} is the "Put it anywhere" box: any doorway, no area
     * layout.
     */
    public static List<SitePlan> spots(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, boolean anywhere) {
        String kind = Areas.areaOf(typeId);
        List<SitePlan> out = new ArrayList<>();
        if (!anywhere) {
            for (String key : Areas.instances(site, kind)) {
                if (!hasHalls(site, key)) continue;
                out.addAll(inArea(site, survey, typeId, size, max, key));
                if (!out.isEmpty()) break;
            }
            if (out.isEmpty()) out.addAll(newArea(site, survey, typeId, size, max, kind));
        }
        if (out.isEmpty()) {
            for (SitePlan p : loose(site, survey.input(), typeId, size, max)) {
                p.note = name(typeId) + " → " + (anywhere ? "the nearest doorway" : "the nearest doorway (no room left for a " + kindName(kind) + " area)");
                out.add(p);
            }
        }
        for (SitePlan p : out) {
            if (p.area == null) for (SitePlan.PlanPart pp : p.parts) if (Areas.ROLE_ROOM.equals(pp.role) && pp.area != null) p.area = pp.area;
            joinLoops(p, site, survey);
        }
        return out;
    }

    private static boolean hasHalls(Site site, String key) {
        for (PlacedPart p : site.parts) if (!p.removed && key.equals(p.area) && isHall(p.role)) return true;
        return false;
    }

    private static boolean isHall(String role) {
        return Areas.ROLE_MAIN_HALL.equals(role) || Areas.ROLE_SIDE_HALL.equals(role);
    }

    /** Room in an existing area, in the fill order of §3.1. */
    private static List<SitePlan> inArea(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, String key) {
        Assembler.Input in = survey.input();
        Areas.Area def = Areas.get(Areas.kindOf(key));
        String where = Areas.displayName(site, key);
        List<SitePlan> out = new ArrayList<>();
        // Rooms and halls mustn't wall off the layout's last free end, or no new area could join.
        boolean guard = layoutEndsAfter(site, survey, in, new SitePlan()) > 0;
        Predicate<SitePlan> keeps = p -> !guard || layoutEndsAfter(site, survey, in, p) > 0;
        // 1. A free door slot on one of its halls.
        Set<Integer> slots = starts(site, survey, p -> key.equals(p.area) && isHall(p.role), SitePart.DOOR);
        if (!slots.isEmpty()) {
            for (SitePlan p : Assembler.spots(in, roomGoal(typeId, size, key), max + 2, slots, null, 12, 0)) {
                if (out.size() >= max || !keeps.test(p)) continue;
                p.note = name(typeId) + " → " + where;
                out.add(p);
            }
        }
        if (!out.isEmpty()) return out;
        // 2. One more piece on a side hall that isn't full, or a new side hall off the main hall.
        SortedSet<Integer> sides = Areas.sideHalls(site, key);
        for (int h : sides) {
            if (out.size() >= max || Areas.hallLength(site, key, Areas.ROLE_SIDE_HALL, h) >= def.sideHall) continue;
            Set<Integer> ends = starts(site, survey, p -> key.equals(p.area) && Areas.ROLE_SIDE_HALL.equals(p.role) && p.hall == h, SitePart.HALL);
            for (SitePlan p : grow(in, typeId, size, key, ends, SitePart.TAG_SIDE_HALL, Areas.ROLE_SIDE_HALL, max - out.size() + 2)) {
                if (out.size() >= max || !keeps.test(p)) continue;
                setHall(p, h);
                p.note = name(typeId) + " → " + where + ", side hall " + h;
                out.add(p);
            }
        }
        if (out.isEmpty() && sides.size() < def.sideHalls) {
            int h = sides.isEmpty() ? 1 : sides.last() + 1;
            Set<Integer> branches = starts(site, survey, p -> key.equals(p.area) && Areas.ROLE_MAIN_HALL.equals(p.role), SitePart.HALL);
            for (SitePlan p : grow(in, typeId, size, key, branches, SitePart.TAG_SIDE_HALL, Areas.ROLE_SIDE_HALL, max + 2)) {
                if (out.size() >= max || !keeps.test(p)) continue;
                setHall(p, h);
                p.note = name(typeId) + " → " + where + ", new side hall";
                out.add(p);
            }
        }
        if (!out.isEmpty()) return out;
        // 3. One more main hall piece.
        if (Areas.hallLength(site, key, Areas.ROLE_MAIN_HALL, 0) < def.mainHall) {
            Set<Integer> ends = starts(site, survey, p -> key.equals(p.area) && Areas.ROLE_MAIN_HALL.equals(p.role), SitePart.HALL);
            for (SitePlan p : grow(in, typeId, size, key, ends, SitePart.TAG_MAIN_HALL, Areas.ROLE_MAIN_HALL, max + 2)) {
                if (out.size() >= max || !keeps.test(p)) continue;
                p.note = name(typeId) + " → " + where + ", longer main hall";
                out.add(p);
            }
        }
        return out;
    }

    private static Assembler.Goal roomGoal(String typeId, SitePart size, String key) {
        Assembler.Goal g = new Assembler.Goal(typeId, false, null, size);
        g.area = key;
        return g;
    }

    /**
     * One hall piece with this tag, then the room on one of its door slots; or two pieces when the room is too
     * wide to sit on the first one (it would reach back over the hall it branches from).
     */
    private static List<SitePlan> grow(Assembler.Input in, String typeId, SitePart size, String key, Set<Integer> starts, String tag, String role, int max) {
        if (starts.isEmpty() || max <= 0) return List.of();
        Assembler.Goal g = roomGoal(typeId, size, key);
        g.startRole = role;
        g.then(SitePart.Kind.HALLWAY, tag, role, null, role);
        return Assembler.spots(in, g, max, starts, null, 12, 1);
    }

    private static void setHall(SitePlan plan, int hall) {
        for (SitePlan.PlanPart pp : plan.parts) if (Areas.ROLE_SIDE_HALL.equals(pp.role)) pp.hall = hall;
    }

    /** A new area: its checkpoint off the layout (a hub arm, the spine, or a tunnel), a main hall piece and the room. */
    private static List<SitePlan> newArea(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, String kind) {
        Assembler.Input in = survey.input();
        Areas.Layout layout = Areas.layout(site);
        // An area that has rooms but no halls yet (the Entrance room, rooms from before v4) gets them now.
        String key = Areas.next(site, kind);
        for (String k : Areas.instances(site, kind)) if (!hasHalls(site, k)) {
            key = k;
            break;
        }
        Areas.Area def = Areas.get(kind);
        Integer storey = def == null ? null : Areas.storey(site, def.layer);
        BlockPos toward = Areas.slot(site, kind, in.bounds, in.baseFloor);
        Set<Integer> starts = starts(site, survey, p -> p.area == null && layoutRole(p.role), SitePart.HALL);
        String label = Areas.displayName(site, key);
        List<Integer> storeys = new ArrayList<>();
        storeys.add(storey);
        if (storey != null && site.type != SiteType.SURFACE_FLAT) storeys.add(Areas.further(site, storey));
        List<SitePlan> out = new ArrayList<>();
        for (Integer s : storeys) {
            if (!out.isEmpty()) break;
            int depth = (layout == Areas.Layout.HUB ? 1 : 4) + (s == null ? 0 : Math.abs(s));
            // Layered sites get a layout piece (hub or spine) on every storey their areas use.
            boolean needPiece = s != null && site.type != SiteType.SURFACE_FLAT && layout != Areas.Layout.SECTORS && !hasLayoutPiece(site, layout, s);
            for (boolean extra : needPiece ? new boolean[]{true} : new boolean[]{false, true}) {
                if (!out.isEmpty()) break;
                Assembler.Goal g = new Assembler.Goal(typeId, false, null, size);
                if (extra) {
                    // No free arm or branch: chain another hub, or a spine piece, first.
                    Assembler.Goal piece = Assembler.layoutPiece(layout, s);
                    g.then(piece, Assembler.layoutFiller(layout));
                }
                Assembler.areaGoal(layout, key, g, s);
                // Chaining a new layout piece may first run a hall out to open ground.
                for (SitePlan p : Assembler.spots(in, g, max, starts, toward, 10, extra ? depth + 2 : depth)) {
                    if (layoutEndsAfter(site, survey, in, p) == 0) continue;
                    p.area = key;
                    p.newArea = true;
                    p.note = name(typeId) + " → new " + label + " area (checkpoint and main hall)";
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Pieces new areas can join: hubs, spine pieces, tunnels and the plain halls between them. */
    private static boolean layoutRole(String role) {
        return Areas.ROLE_HUB.equals(role) || Areas.ROLE_SPINE.equals(role) || Areas.ROLE_TUNNEL.equals(role) || Areas.ROLE_LINK.equals(role);
    }

    /**
     * Open hall ends the layout would have left after this plan. A plan that would use up the last one is turned
     * down, so the next area chains another hub (or the spine grows) instead of the layout running out of ends.
     */
    private static int layoutEndsAfter(Site site, SiteGen.Survey survey, Assembler.Input in, SitePlan plan) {
        List<Box> boxes = new ArrayList<>();
        for (SitePlan.PlanPart pp : plan.parts) boxes.add(pp.box);
        int n = 0;
        for (int i = 0; i < survey.sources().size(); i++) {
            Doorway d = survey.sources().get(i);
            if (plan.openedDoorways.contains(i) || !SitePart.HALL.equals(d.type) || d.part < 0 || d.part >= site.parts.size()) continue;
            PlacedPart owner = site.parts.get(d.part);
            if (!owner.removed && owner.area == null && layoutRole(owner.role) && Assembler.clearAhead(in, d.pos, d.facing, AHEAD, boxes)) n++;
        }
        for (SitePlan.PlanPart pp : plan.parts) {
            if (pp.area != null || !layoutRole(pp.role)) continue;
            for (int c = 0; c < pp.part.connectors.size(); c++) {
                SitePart.Connector con = pp.connector(c);
                if ((pp.used & (1 << c)) == 0 && SitePart.HALL.equals(con.type()) && Assembler.clearAhead(in, con.pos(), con.facing(), AHEAD, boxes)) n++;
            }
        }
        return n;
    }

    /** How far a layout end must lead into open ground to count: room for a checkpoint. */
    private static final int AHEAD = 10;

    private static boolean hasLayoutPiece(Site site, Areas.Layout layout, int storey) {
        String role = layout == Areas.Layout.HUB ? Areas.ROLE_HUB : Areas.ROLE_SPINE;
        for (PlacedPart p : site.parts) if (!p.removed && role.equals(p.role) && p.storey == storey) return true;
        return false;
    }

    /** The search from before areas (§8.1): any doorway, the preferred storey first. */
    public static List<SitePlan> loose(Site site, Assembler.Input in, String typeId, SitePart size, int max) {
        List<SitePlan> out = new ArrayList<>();
        Integer storey = SiteGen.preferredStorey(site, in);
        if (storey != null && in.near == null) out.addAll(Assembler.spots(in, new Assembler.Goal(typeId, false, storey, size), max));
        if (out.size() < max) {
            Set<String> have = new HashSet<>();
            for (SitePlan p : out) have.add(p.key());
            for (SitePlan p : Assembler.spots(in, new Assembler.Goal(typeId, false, null, size), max)) {
                if (out.size() >= max) break;
                if (have.add(p.key())) out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- loops (V4)

    /**
     * Where a new hall piece's open end lines up with another open hall end of the same area, both are opened
     * so the halls loop back into each other. Ends that line up with nothing stay walled: dead ends.
     */
    static void joinLoops(SitePlan plan, Site site, SiteGen.Survey survey) {
        List<SitePlan.PlanPart> parts = plan.parts;
        for (int i = 0; i < parts.size(); i++) {
            SitePlan.PlanPart a = parts.get(i);
            if (a.area == null || !isHall(a.role)) continue;
            for (int ci = 0; ci < a.part.connectors.size(); ci++) {
                if ((a.used & (1 << ci)) != 0) continue;
                SitePart.Connector c = a.connector(ci);
                if (!c.type().equals(SitePart.HALL)) continue;
                BlockPos facingPos = c.pos().relative(c.facing());
                boolean joined = false;
                for (int j = i + 1; j < parts.size() && !joined; j++) {
                    SitePlan.PlanPart b = parts.get(j);
                    if (!a.area.equals(b.area) || !isHall(b.role)) continue;
                    for (int cj = 0; cj < b.part.connectors.size(); cj++) {
                        if ((b.used & (1 << cj)) != 0) continue;
                        SitePart.Connector d = b.connector(cj);
                        if (d.type().equals(SitePart.HALL) && d.pos().equals(facingPos) && d.facing() == c.facing().getOpposite()) {
                            a.used |= 1 << ci;
                            b.used |= 1 << cj;
                            joined = true;
                            break;
                        }
                    }
                }
                if (joined || survey == null) continue;
                for (int k = 0; k < survey.sources().size(); k++) {
                    Doorway d = survey.sources().get(k);
                    if (plan.openedDoorways.contains(k) || !SitePart.HALL.equals(d.type) || d.part < 0 || d.part >= site.parts.size()) continue;
                    PlacedPart owner = site.parts.get(d.part);
                    if (owner.removed || !a.area.equals(owner.area) || !isHall(owner.role)) continue;
                    if (d.pos.equals(facingPos) && d.facing == c.facing().getOpposite()) {
                        a.used |= 1 << ci;
                        plan.openedDoorways.add(k);
                        break;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Indexes of survey doorways of this type whose part passes the test (doorways of hand-designated rooms count as the core). */
    private static Set<Integer> starts(Site site, SiteGen.Survey survey, Predicate<PlacedPart> test, String type) {
        Set<Integer> out = new LinkedHashSet<>();
        PlacedPart core = new PlacedPart();
        for (int i = 0; i < survey.sources().size(); i++) {
            Doorway d = survey.sources().get(i);
            if (type != null && !type.equals(d.type)) continue;
            PlacedPart owner = d.part >= 0 && d.part < site.parts.size() ? site.parts.get(d.part) : core;
            if (!owner.removed && test.test(owner)) out.add(i);
        }
        return out;
    }

    private static String kindName(String kind) {
        Areas.Area a = Areas.get(kind);
        return a == null ? kind : a.name;
    }

    private static String name(String typeId) {
        RoomType t = RoomTypes.get(typeId);
        return t == null ? typeId : t.name;
    }

    private Skeleton() {}
}
