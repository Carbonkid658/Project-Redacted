package dev.projectredacted.room;

import dev.projectredacted.site.Site;
import dev.projectredacted.staff.StaffRole;

/** Whether a room type is available yet, and the names checkpoints and entrances get. */
public final class RoomPlanner {
    /** Why this room type isn't available yet, or null. */
    public static String lockedReason(Site site, RoomType type) {
        if (site.level < type.requiredLevel) return "Requires Facility Level " + type.requiredLevel;
        if (type.unique && !site.rooms(type.id).isEmpty()) return "Only one per site";
        dev.projectredacted.site.Upgrade upgrade = type.upgrade();
        if (upgrade != null && !site.hasUpgrade(upgrade)) return "Requires the " + upgrade.displayName + " upgrade";
        StaffRole role = type.role();
        if (role != null && site.countStaff(role) == 0) return "Requires a " + role.displayName + " on staff";
        return null;
    }

    /** Checkpoints and entrances get names the clipboard can target. */
    public static void name(Site site, Room room, RoomType type) {
        if (type.isCheckpoint()) {
            int n = 0;
            for (Room r : site.rooms) if (r.typeDef() != null && r.typeDef().isCheckpoint()) n++;
            room.name = "Checkpoint " + (char) ('A' + (n % 26));
        } else if (type.id.equals(RoomTypes.ENTRANCE)) {
            room.name = "Entrance " + (site.rooms(RoomTypes.ENTRANCE).size() + 1);
        }
    }

    private RoomPlanner() {}
}
