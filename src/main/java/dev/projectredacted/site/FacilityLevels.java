package dev.projectredacted.site;

import dev.projectredacted.staff.StaffRole;

/**
 * What a site needs before each facility level upgrade. Level 1 is from the design doc (1 SCP, 1 Scientist,
 * 1 Security). Levels 2 and 3 are placeholder defaults until the real criteria are decided.
 */
public final class FacilityLevels {
    private static final int[][] NEEDS = {
            // scps, researchers, security, engineers, medical, administration
            {0, 0, 0, 0, 0, 0},
            {1, 1, 1, 0, 0, 0},
            {3, 3, 3, 1, 1, 0},
            {6, 6, 6, 2, 2, 1},
    };

    /** What's still missing for the given level, or null if the site qualifies. */
    public static String missing(Site site, int level) {
        int[] n = NEEDS[Math.max(0, Math.min(3, level))];
        StringBuilder sb = new StringBuilder();
        need(sb, site.scpCount, n[0], "SCP contained");
        need(sb, site.countStaff(StaffRole.RESEARCHER), n[1], "Researcher");
        need(sb, site.countStaff(StaffRole.SECURITY), n[2], "Security");
        need(sb, site.countStaff(StaffRole.ENGINEER), n[3], "Engineer");
        need(sb, site.countStaff(StaffRole.MEDICAL), n[4], "Medical");
        need(sb, site.countStaff(StaffRole.ADMINISTRATION), n[5], "Administration");
        return sb.isEmpty() ? null : "Needs " + sb;
    }

    private static void need(StringBuilder sb, int have, int want, String what) {
        if (have >= want) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(want - have).append(' ').append(what);
    }

    private FacilityLevels() {}
}
