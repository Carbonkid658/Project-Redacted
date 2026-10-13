package dev.projectredacted.site.gen;

import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteType;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.function.Predicate;

/**
 * The site skeleton (design §4A.5): decides where in the facility a new room goes before the assembler places
 * parts. A room goes to its wing, into a section with space or a new section off the wing corridor; a wing that
 * doesn't exist yet is founded in its slot with a gate, a corridor and its first section, joined by the site's
 * connection style (hub, spine or sectors).
 */
public final class Skeleton {
    /** Roles of parts that belong to the wing they lead into. */
    private static final Set<String> WING_ROLES = Set.of(Wings.ROLE_TRANSITION, Wings.ROLE_TUNNEL, Wings.ROLE_GATE, Wings.ROLE_CORRIDOR,
            Wings.ROLE_SECTION_GATE, Wings.ROLE_SECTION, Wings.ROLE_ROOM);
    private static final Set<String> SECTION_ROLES = Set.of(Wings.ROLE_SECTION_GATE, Wings.ROLE_SECTION, Wings.ROLE_ROOM);

    /**
     * Up to {@code max} spots for a room. {@code anywhere} is the "Put it anywhere" box: any doorway, no wing
     * layout (the v2 behaviour).
     */
    public static List<SitePlan> spots(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, boolean anywhere) {
        String wing = Wings.wingFor(site, typeId);
        List<SitePlan> out = new ArrayList<>();
        if (!anywhere) {
            out.addAll(inWing(site, survey, typeId, size, max, wing));
            if (out.isEmpty() && isOutpostWing(wing)) out.addAll(outpost(site, survey, typeId, size, max, wing));
        }
        if (out.isEmpty()) {
            for (SitePlan p : loose(site, survey.input(), typeId, size, max)) {
                tag(p, wing, 0, false, false);
                p.note = name(typeId) + " → " + (anywhere ? "the nearest doorway" : "the nearest doorway (no room left for " + Wings.title(wing) + ")");
                out.add(p);
            }
        }
        return out;
    }

    /** The A, B and C functions start inside the outpost hub (§4A.2). */
    private static boolean isOutpostWing(String wing) {
        return "A".equals(wing) || "B".equals(wing) || "C".equals(wing);
    }

    private static List<SitePlan> inWing(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, String wing) {
        Assembler.Input in = survey.input();
        Wings.WingState ws = Wings.state(site, wing);
        Wings.Wing def = Wings.get(wing);
        Integer storey = def == null ? null : Wings.storey(site, def.layer);
        String group = Wings.group(typeId);
        List<SitePlan> out = new ArrayList<>();
        if (ws.founded) {
            // A section of the same kind with space (§4A.3).
            for (Wings.Section sec : ws.sections) {
                if (out.size() >= max) break;
                if (!group.equals(sec.group) || Wings.rooms(site, wing, sec.number) >= Wings.SECTION_ROOMS) continue;
                Set<Integer> starts = starts(site, survey, p -> wing.equals(p.wing) && p.section == sec.number);
                if (starts.isEmpty()) continue;
                Assembler.Goal g = new Assembler.Goal(typeId, false, null, size);
                g.wing = wing;
                g.startRole = Wings.ROLE_SECTION;
                for (SitePlan p : Assembler.spots(in, g, max - out.size(), starts, null, 8, 2)) {
                    tag(p, wing, sec.number, false, false);
                    p.note = name(typeId) + " → " + Wings.title(wing) + ", section " + Wings.sectionLabel(wing, sec.number);
                    out.add(p);
                }
            }
            // A new section off the wing corridor.
            if (out.size() < max) {
                Set<Integer> starts = starts(site, survey, p -> wing.equals(p.wing) && (Wings.ROLE_CORRIDOR.equals(p.role) || Wings.ROLE_GATE.equals(p.role)));
                int n = ws.nextSection();
                Assembler.Goal g = new Assembler.Goal(typeId, false, null, size);
                g.wing = wing;
                g.startRole = Wings.ROLE_CORRIDOR;
                g.then(SitePart.Kind.GATE, SitePart.TAG_SECTION, Wings.ROLE_SECTION_GATE, null, Wings.ROLE_SECTION);
                if (!starts.isEmpty()) for (SitePlan p : Assembler.spots(in, g, max - out.size(), starts, null, 8, 3)) {
                    tag(p, wing, n, false, true);
                    p.note = name(typeId) + " → " + Wings.title(wing) + ", new section " + Wings.sectionLabel(wing, n);
                    out.add(p);
                }
            }
            return out;
        }
        // Found the wing in its slot: gate, corridor and the first section (§4A.5).
        Set<Integer> starts = starts(site, survey, p -> p.wing == null);
        BlockPos toward = Wings.slot(site, wing, in.bounds, in.baseFloor);
        List<Integer> storeys = new ArrayList<>();
        storeys.add(storey);
        if (storey != null && site.type != SiteType.SURFACE_FLAT) storeys.add(Wings.further(site, storey));
        for (Integer s : storeys) {
            if (!out.isEmpty()) break;
            Assembler.Goal g = founding(site, typeId, size, wing, s);
            int depth = 3 + (s == null ? 0 : Math.abs(s));
            for (SitePlan p : Assembler.spots(in, g, max, starts, toward, 8, depth)) {
                tag(p, wing, 1, true, true);
                p.note = name(typeId) + " → founds " + Wings.title(wing) + " (gate, corridor and section " + Wings.sectionLabel(wing, 1) + ")";
                out.add(p);
            }
        }
        return out;
    }

    /** The parts between the facility and a new wing's first room, by connection style (§4A.4). */
    private static Assembler.Goal founding(Site site, String typeId, SitePart size, String wing, Integer storey) {
        Assembler.Goal g = new Assembler.Goal(typeId, false, storey, size);
        g.wing = wing;
        Wings.Wing def = Wings.get(wing);
        // E and F always connect through a Transition Checkpoint.
        String gateTag = def != null && def.layer == Wings.Layer.CONTAINMENT ? SitePart.TAG_TRANSITION : null;
        switch (Wings.connection(site)) {
            case HUB -> {
                g.startRole = Wings.ROLE_LINK;
                // Layered sites get a hub on every layer they use.
                if (storey != null && site.type != SiteType.SURFACE_FLAT && !hasPart(site, Wings.ROLE_HUB, storey))
                    g.then(SitePart.Kind.HUB, null, Wings.ROLE_HUB, storey, Wings.ROLE_LINK);
                g.then(SitePart.Kind.GATE, gateTag, Wings.ROLE_GATE, null, Wings.ROLE_CORRIDOR);
            }
            case SPINE -> {
                g.startRole = Wings.ROLE_SPINE;
                if (!hasPart(site, Wings.ROLE_SPINE, storey)) g.then(SitePart.Kind.HALLWAY, SitePart.TAG_SPINE, Wings.ROLE_SPINE, storey, Wings.ROLE_SPINE);
                g.then(SitePart.Kind.GATE, gateTag, Wings.ROLE_GATE, null, Wings.ROLE_CORRIDOR);
            }
            case SECTORS -> {
                g.startRole = Wings.ROLE_LINK;
                g.then(SitePart.Kind.GATE, SitePart.TAG_TRANSITION, Wings.ROLE_TRANSITION, null, Wings.ROLE_TUNNEL);
                g.then(SitePart.Kind.HALLWAY, SitePart.TAG_TUNNEL, Wings.ROLE_TUNNEL, null, Wings.ROLE_TUNNEL);
                g.then(SitePart.Kind.GATE, SitePart.TAG_TRANSITION, Wings.ROLE_GATE, null, Wings.ROLE_CORRIDOR);
            }
        }
        g.then(SitePart.Kind.HALLWAY, null, Wings.ROLE_CORRIDOR, null, Wings.ROLE_CORRIDOR);
        g.then(SitePart.Kind.GATE, SitePart.TAG_SECTION, Wings.ROLE_SECTION_GATE, null, Wings.ROLE_SECTION);
        return g;
    }

    /** Until A, B or C has room for its own wing, its rooms hang off the outpost hub. */
    private static List<SitePlan> outpost(Site site, SiteGen.Survey survey, String typeId, SitePart size, int max, String wing) {
        Set<Integer> starts = starts(site, survey, p -> Wings.ROLE_HUB.equals(p.role) || (p.wing == null && Wings.ROLE_LINK.equals(p.role)));
        if (starts.isEmpty()) return List.of();
        List<SitePlan> out = new ArrayList<>();
        for (SitePlan p : Assembler.spots(survey.input(), new Assembler.Goal(typeId, false, null, size), max, starts, null, 8, 3)) {
            tag(p, wing, 0, false, false);
            for (SitePlan.PlanPart pp : p.parts) if (!Wings.ROLE_ROOM.equals(pp.role)) pp.wing = null;
            p.note = name(typeId) + " → the outpost hub (" + Wings.title(wing) + " gets its own wing when there's space)";
            out.add(p);
        }
        return out;
    }

    /** The v2 search: any doorway, the preferred storey first (§8.1). */
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

    // ---------------------------------------------------------------- helpers

    /** Indexes of survey doorways whose part passes the test (doorways of hand-designated rooms count as the core). */
    private static Set<Integer> starts(Site site, SiteGen.Survey survey, Predicate<PlacedPart> test) {
        Set<Integer> out = new LinkedHashSet<>();
        PlacedPart core = new PlacedPart();
        for (int i = 0; i < survey.sources().size(); i++) {
            Doorway d = survey.sources().get(i);
            PlacedPart owner = d.part >= 0 && d.part < site.parts.size() ? site.parts.get(d.part) : core;
            if (!owner.removed && test.test(owner)) out.add(i);
        }
        return out;
    }

    private static boolean hasPart(Site site, String role, Integer storey) {
        for (PlacedPart p : site.parts) if (!p.removed && role.equals(p.role) && (storey == null || p.storey == storey)) return true;
        return false;
    }

    /** Marks which parts of a plan belong to the wing and section. */
    static void tag(SitePlan plan, String wing, int section, boolean newWing, boolean newSection) {
        plan.wing = wing;
        plan.section = section;
        plan.newWing = newWing;
        plan.newSection = newSection;
        for (SitePlan.PlanPart pp : plan.parts) {
            if (pp.role != null && WING_ROLES.contains(pp.role)) pp.wing = wing;
            if (section > 0 && pp.role != null && SECTION_ROLES.contains(pp.role)) pp.section = section;
        }
    }

    private static String name(String typeId) {
        RoomType t = RoomTypes.get(typeId);
        return t == null ? typeId : t.name;
    }

    private Skeleton() {}
}
