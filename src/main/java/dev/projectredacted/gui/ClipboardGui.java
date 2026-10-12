package dev.projectredacted.gui;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.staff.Assignment;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Writing a target on the clipboard. What a role can be given follows the design:
 * living areas by room number; researchers an SCP number; security a checkpoint, an SCP (they stand outside the
 * cell), a room number or an entrance.
 */
public final class ClipboardGui {
    public static void openRoster(ServerPlayer player, Site site, int page) {
        List<StaffRecord> staff = new ArrayList<>(site.staff.values());
        ChestGui gui = new ChestGui("Clipboard: Roster", 6);
        int perPage = 45, pages = Math.max(1, (staff.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage && page * perPage + i < staff.size(); i++) {
            StaffRecord r = staff.get(page * perPage + i);
            gui.set(i, ChestGui.icon(r.role.icon(), r.fullName(), r.named ? ChatFormatting.GOLD : ChatFormatting.WHITE,
                    r.role.displayName + ", level " + r.level + ", clearance " + r.clearance,
                    "Assignment: " + StaffEntity.describeAssignment(site, r.assignment),
                    String.format("Morale %.0f, food %.0f, rest %.0f", r.morale, r.hunger, r.energy),
                    "Use the clipboard on them to assign a task."));
        }
        final int current = page;
        if (page > 0) gui.button(45, ChestGui.icon(Items.ARROW, "Previous page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openRoster(p, site, current - 1)));
        if (page < pages - 1) gui.button(53, ChestGui.icon(Items.ARROW, "Next page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> openRoster(p, site, current + 1)));
        gui.footer(null);
        gui.open(player);
    }

    public static void openAssign(ServerPlayer player, Site site, StaffRecord rec) {
        ChestGui gui = new ChestGui("Assign: " + rec.name, 6);
        List<ItemStack> icons = new ArrayList<>();
        List<Assignment> targets = new ArrayList<>();
        for (Room room : site.rooms) {
            RoomType t = room.typeDef();
            if (t == null || !room.built) continue;
            Assignment.Kind kind = kindFor(rec.role, room, t);
            if (kind == null) continue;
            if (t.clearance > rec.clearance && kind != Assignment.Kind.SCP) continue; // clearance keeps staff out of rooms above them
            String label = switch (kind) {
                case SCP -> (room.scpLabel.isEmpty() ? "Empty cell" : room.scpLabel) + " (cell #" + room.number + ")";
                case CHECKPOINT, ENTRANCE -> room.displayName();
                default -> "Room " + room.number + ": " + t.name;
            };
            icons.add(ChestGui.icon(t.iconItem(), label, ChatFormatting.AQUA, kindHint(rec.role, kind)));
            targets.add(Assignment.of(kind, room.number));
        }
        for (int i = 0; i < Math.min(45, icons.size()); i++) {
            Assignment a = targets.get(i);
            gui.button(i, icons.get(i), (p, r) -> {
                rec.assignment = a;
                p.sendSystemMessage(Component.literal(rec.name + " assigned to " + StaffEntity.describeAssignment(site, a) + ".").withStyle(ChatFormatting.GREEN));
                p.closeContainer();
            });
        }
        gui.button(49, ChestGui.icon(Items.BARRIER, "Clear assignment", ChatFormatting.RED, "They'll go back to their role's usual post."), (p, r) -> {
            rec.assignment = new Assignment();
            p.closeContainer();
        });
        gui.footer(null);
        gui.open(player);
    }

    private static Assignment.Kind kindFor(StaffRole role, Room room, RoomType t) {
        boolean entrance = room.type.equals(RoomTypes.ENTRANCE);
        return switch (role) {
            case RESEARCHER -> t.isContainment() ? Assignment.Kind.SCP : t.category.equals("Research") || t.category.equals("Offices") ? Assignment.Kind.ROOM : null;
            case SECURITY -> t.isContainment() ? Assignment.Kind.SCP : t.isCheckpoint() ? Assignment.Kind.CHECKPOINT : entrance ? Assignment.Kind.ENTRANCE : Assignment.Kind.ROOM;
            case DCLASS -> t.isContainment() || t.category.equals("Research") || room.type.equals("projectredacted:prison") ? Assignment.Kind.ROOM : null;
            default -> t.isContainment() ? null : Assignment.Kind.ROOM;
        };
    }

    private static String kindHint(StaffRole role, Assignment.Kind kind) {
        return switch (kind) {
            case SCP -> role == StaffRole.SECURITY ? "Stand guard outside the cell." : "Research this SCP (observation and hands-on).";
            case CHECKPOINT -> "Guard this checkpoint.";
            case ENTRANCE -> "Gather near this entrance.";
            default -> role == StaffRole.DCLASS ? "Be led here by a researcher." : "Work in this room.";
        };
    }

    private ClipboardGui() {}
}
