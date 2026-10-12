package dev.projectredacted.room;

import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Checks and commits room designs. */
public final class RoomPlanner {
    /** Why this room type isn't available yet, or null. */
    public static String lockedReason(Site site, RoomType type) {
        if (site.level < type.requiredLevel) return "Requires Facility Level " + type.requiredLevel;
        StaffRole role = type.role();
        if (role != null && site.countStaff(role) == 0) return "Requires a " + role.displayName + " on staff";
        return null;
    }

    /** Why the design can't be placed, or null if it's fine. */
    public static String validate(Site site, DesignSession s, RoomType type) {
        if (s.cornerA == null || s.cornerB == null) return "Mark two corners first.";
        Room draft = s.draftRoom(0);
        int w = draft.x2 - draft.x1 + 1, l = draft.z2 - draft.z1 + 1, h = draft.y2 - draft.y1;
        if (Math.min(w, l) < type.minSide) return type.name + " needs at least " + type.minSide + " blocks per side (got " + w + "x" + l + ").";
        if (Math.max(w, l) > type.maxSide) return type.name + " can be at most " + type.maxSide + " blocks per side.";
        if (h < type.minHeight) return type.name + " needs at least " + type.minHeight + " blocks between floor and ceiling (got " + h + ").";
        if (h > type.maxHeight) return type.name + " can be at most " + type.maxHeight + " blocks tall.";
        if (!site.containsRoom(draft)) return "The room must be fully inside your claim.";
        for (Room other : site.rooms) if (other.interiorIntersects(draft)) return "That overlaps " + other.displayName() + ".";
        return lockedReason(site, type);
    }

    public static Cost cost(RoomType type, DesignSession s) {
        Room draft = s.draftRoom(0);
        return type.cost().scaled(Math.max(1.0, draft.floorArea() / 49.0));
    }

    /** Pays for and starts building the designed room. Returns the new room, or null with a message sent. */
    public static Room commit(Site site, Player player, DesignSession s, boolean freeform) {
        RoomType type = RoomTypes.get(s.typeId);
        if (type == null) return null;
        String problem = validate(site, s, type);
        if (problem != null) {
            player.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            return null;
        }
        if (!freeform) {
            Cost cost = cost(type, s);
            if (!cost.pay(player)) {
                player.sendSystemMessage(Component.literal("Not enough materials. Needs: " + String.join(", ", cost.describe())).withStyle(ChatFormatting.RED));
                return null;
            }
        }
        Room room = s.draftRoom(site.nextRoomNumber++);
        room.freeform = freeform;
        room.built = freeform;
        name(site, room, type);
        site.rooms.add(room);
        s.reset();
        String msg = freeform ? room.displayName() + " registered." : "Construction of " + room.displayName() + " has begun.";
        if (!Cassie.announce(site, msg)) player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        return room;
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
