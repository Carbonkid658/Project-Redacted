package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.Assignment;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writing a task on the clipboard (GUI design §7.2). Tabs show only the kinds of target the role can take;
 * targets above the staff member's clearance are listed greyed. Select, then Assign, so a misclick doesn't
 * reassign anyone.
 */
public class AssignScreen extends GuiScreen {
    private static final Map<Assignment.Kind, String> TABS = new LinkedHashMap<>();

    static {
        TABS.put(Assignment.Kind.SCP, "SCPs");
        TABS.put(Assignment.Kind.ROOM, "Rooms");
        TABS.put(Assignment.Kind.CHECKPOINT, "Checkpoints");
        TABS.put(Assignment.Kind.ENTRANCE, "Entrances");
    }

    private final Site site;
    private final StaffRecord rec;
    private Assignment.Kind tab;
    private int selected = -1;

    public AssignScreen(Site site, StaffRecord rec) {
        this.site = site;
        this.rec = rec;
    }

    @Override
    public String id() {
        return "assign";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || site.staff.get(rec.id) != rec) return "They no longer work at your site.";
        return StaffViews.orderBlocker(site, rec, player);
    }

    private List<Assignment.Kind> kinds() {
        List<Assignment.Kind> kinds = new ArrayList<>();
        switch (rec.role) {
            case RESEARCHER -> kinds.addAll(List.of(Assignment.Kind.SCP, Assignment.Kind.ROOM));
            case SECURITY -> kinds.addAll(List.of(Assignment.Kind.SCP, Assignment.Kind.ROOM, Assignment.Kind.CHECKPOINT, Assignment.Kind.ENTRANCE));
            default -> kinds.add(Assignment.Kind.ROOM);
        }
        return kinds;
    }

    /** The kind of task this room is for this staff member, or null if they can't be sent there. */
    private Assignment.Kind kindFor(Room room, RoomType t) {
        boolean entrance = room.type.equals(RoomTypes.ENTRANCE);
        return switch (rec.role) {
            case RESEARCHER -> t.isContainment() ? Assignment.Kind.SCP : t.category.equals("Research") || t.category.equals("Offices") ? Assignment.Kind.ROOM : null;
            case SECURITY -> t.isContainment() ? Assignment.Kind.SCP : t.isCheckpoint() ? Assignment.Kind.CHECKPOINT : entrance ? Assignment.Kind.ENTRANCE : Assignment.Kind.ROOM;
            case DCLASS -> t.isContainment() || t.category.equals("Research") || room.type.equals("projectredacted:prison") ? Assignment.Kind.ROOM : null;
            default -> t.isContainment() ? null : Assignment.Kind.ROOM;
        };
    }

    private String hint(Assignment.Kind kind) {
        return switch (kind) {
            case SCP -> rec.role == StaffRole.SECURITY ? "Guard outside the cell" : "Research (observation + contact)";
            case CHECKPOINT -> "Guard this checkpoint";
            case ENTRANCE -> "Gather near this entrance";
            default -> rec.role == StaffRole.DCLASS ? "Be led here by a researcher" : "Work in this room";
        };
    }

    /** Why this room can't be picked (clearance), or null. */
    private String blocker(Room room, RoomType t, Assignment.Kind kind) {
        return t.clearance > rec.clearance && kind != Assignment.Kind.SCP ? "Clearance " + t.clearance + " required" : null;
    }

    @Override
    public View build(ServerPlayer p) {
        List<Assignment.Kind> kinds = kinds();
        if (tab == null || !kinds.contains(tab)) tab = kinds.get(0);
        View v = new View("ASSIGN · " + rec.name + " (" + rec.role.displayName + ", CLR " + rec.clearance + ")").face(Ui.staffSkin(rec.role, rec.skin)).badge(Ui.clr(site, p));
        v.add(El.text("Current: " + StaffEntity.describeAssignment(site, rec.assignment), "amber"));
        String[] chips = new String[kinds.size() * 2];
        for (int i = 0; i < kinds.size(); i++) {
            chips[2 * i] = kinds.get(i).name();
            chips[2 * i + 1] = TABS.get(kinds.get(i));
        }
        v.add(El.chips("tab", tab.name(), chips));
        El.ListEl list = El.list("targets", "select").searchable().empty("Nothing of this kind at your site yet.").fill();
        for (Room room : site.rooms) {
            RoomType t = room.typeDef();
            if (t == null || !room.built || kindFor(room, t) != tab) continue;
            String label = switch (tab) {
                case SCP -> (room.scpLabel.isEmpty() ? "Empty cell" : room.scpLabel) + " · " + t.name + " " + room.label();
                case CHECKPOINT, ENTRANCE -> room.displayName();
                default -> room.displayName();
            };
            // Searchable by its area label (Containment-04) and by its plain number.
            if (room.area != null) label += " · #" + room.number;
            String blocked = blocker(room, t, tab);
            list.add(String.valueOf(room.number), label).icon(t.iconItem()).sub(blocked != null ? blocked : hint(tab), blocked != null ? "red" : "gray")
                    .dim(blocked != null).selected(room.number == selected);
        }
        v.add(list);
        Room target = site.room(selected);
        String assignBlocker = target == null ? "Pick a target first." : target.typeDef() == null ? "Unknown room." : blocker(target, target.typeDef(), tab);
        v.add(El.row(El.button("Clear assignment", "clear").disabled(rec.assignment.kind == Assignment.Kind.NONE ? "They have no assignment." : null)
                        .tip("They'll go back to their role's usual post."),
                El.button("Assign ✔", "assign").style("confirm").disabled(assignBlocker)));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "tab" -> {
                try {
                    tab = Assignment.Kind.valueOf(a.str("id"));
                    selected = -1;
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "select" -> selected = TerminalScreen.parseInt(a.str("id"), -1);
            case "clear" -> {
                rec.assignment = new Assignment();
                Gui.toast(p, rec.name + " goes back to their usual post.", false);
            }
            case "assign" -> {
                Room room = site.room(selected);
                RoomType t = room == null ? null : room.typeDef();
                if (t == null || kindFor(room, t) != tab || blocker(room, t, tab) != null) return;
                rec.assignment = Assignment.of(tab, room.number);
                Gui.toast(p, rec.name + " assigned to " + StaffEntity.describeAssignment(site, rec.assignment) + ".", false);
                if (parent != null) Gui.open(p, parent);
            }
            default -> {}
        }
    }
}
