package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.El;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.GuiScreen;
import dev.projectredacted.gui.Ui;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.Assignment;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** The Staff Card (GUI design §8.1), shared by the NPC, the terminal's Staff tab and the clipboard roster. */
public final class StaffViews {
    public enum Buttons { MANAGE, ORDERS, TERMINAL }

    public static List<El> card(Site site, StaffRecord r, ServerPlayer viewer, Buttons... buttons) {
        List<El> out = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        lines.add(r.role.displayName + " · Level " + r.level + " · CLR " + r.clearance);
        if (r.skill != null) lines.add("Skill: " + r.skill.displayName + " · " + r.skill.description);
        out.add(El.portrait(Ui.staffSkin(r.role, r.skin), r.fullName().toUpperCase(), r.named ? "NAMED" : null, lines));
        double floor = 40.0 * (r.level - 1) * (r.level - 1), next = 40.0 * r.level * r.level;
        out.add(r.level >= 10 ? El.progress("XP", 1, "MAX") : El.progress("XP", (r.xp - floor) / Math.max(1, next - floor), String.format("%.0f / %.0f", r.xp, next)));
        out.add(El.need("Morale", r.morale));
        out.add(El.need("Food", r.hunger));
        out.add(El.need("Rest", r.energy));
        boolean noBed = r.bedIndex < 0 && r.role != StaffRole.DCLASS;
        out.add(El.text(String.format("Productivity %.0f%%", r.productivity * 100), r.productivity < 0.6 ? "amber" : "white"));
        out.add(El.text(r.role == StaffRole.DCLASS ? "Bed: sleeps in the Prison" : noBed ? "NO BED: build more housing" : "Bed: assigned", noBed ? "red" : "gray"));
        out.add(El.text("Assignment: " + StaffEntity.describeAssignment(site, r.assignment), "gray"));
        List<El> row = new ArrayList<>();
        for (Buttons b : buttons) {
            switch (b) {
                case MANAGE -> {
                    String director = site.isDirector(viewer) ? null : "Only the Site Director can do this.";
                    String promote = director != null ? director : r.clearance >= 5 ? "Already at the top clearance."
                            : r.level < r.clearance * 2 && !viewer.isCreative() ? "Needs to reach level " + r.clearance * 2 + " first." : null;
                    row.add(El.button("Promote", "staff_promote").style("confirm").disabled(promote)
                            .tip("New title: " + r.role.title(Math.min(5, r.clearance + 1)), "Needs level " + r.clearance * 2 + "+."));
                    row.add(El.button("Demote", "staff_demote").disabled(director != null ? director : r.clearance <= 0 ? "Already at the lowest clearance." : null)
                            .confirm("Demote " + r.fullName() + "? Their morale drops by 20."));
                    row.add(El.button("Assign task", "staff_assign").disabled(orderBlocker(site, r, viewer)));
                    row.add(El.button("Dismiss", "staff_dismiss").style("danger").disabled(director)
                            .confirm("Dismiss " + r.fullName() + "? They leave the site for good."));
                }
                case ORDERS -> {
                    row.add(El.button("Assign task", "staff_assign").style("primary").disabled(orderBlocker(site, r, viewer)));
                    if (r.role == StaffRole.SECURITY) {
                        boolean following = r.assignment.kind == Assignment.Kind.FOLLOW && viewer.getUUID().equals(r.assignment.leader);
                        row.add(El.button(following ? "Return to post" : "Follow me", "staff_follow").disabled(orderBlocker(site, r, viewer)));
                    }
                }
                case TERMINAL -> row.add(El.button("Open in terminal", "staff_terminal").disabled(terminalBlocker(site, viewer)));
            }
        }
        if (!row.isEmpty()) out.add(El.row(row));
        return out;
    }

    /** Opening the terminal away from the computer works like the Portable Tablet. */
    static String terminalBlocker(Site site, Player player) {
        if (!site.canUseTerminal(player)) return "You don't have terminal access.";
        if (!site.mainframeOnline()) return "The Mainframe is offline.";
        if (!site.hasUpgrade(dev.projectredacted.site.Upgrade.PORTABLE_TABLET)) return "Needs the Portable Tablet upgrade to open remotely.";
        return null;
    }

    /** Why this player can't give the staff member orders, or null. */
    public static String orderBlocker(Site site, StaffRecord r, Player player) {
        if (!site.isMember(player) || !site.canAssign(player)) return "Doesn't take orders from you.";
        if (site.clearance(player) < r.clearance) return "Outranks you. Needs clearance " + r.clearance + ".";
        return null;
    }

    /** Applies a staff card button. Returns false if the action isn't a staff action. */
    public static boolean handle(GuiScreen screen, Site site, StaffRecord r, ServerPlayer player, String action) {
        switch (action) {
            case "staff_promote" -> {
                if (!site.isDirector(player) || r.clearance >= 5) return true;
                if (r.level < r.clearance * 2 && !player.isCreative()) {
                    Gui.toast(player, r.name + " needs to reach level " + r.clearance * 2 + " first.", true);
                    return true;
                }
                r.clearance++;
                r.repickSkin();
                refreshName(r);
                Cassie.announce(site, r.name + " has been promoted to " + r.title() + ".");
                Gui.toast(player, r.name + " promoted to " + r.title() + ".", false);
            }
            case "staff_demote" -> {
                if (!site.isDirector(player) || r.clearance <= 0) return true;
                r.clearance--;
                r.morale = Math.max(0, r.morale - 20);
                refreshName(r);
                Gui.toast(player, r.name + " demoted to " + r.title() + ".", false);
            }
            case "staff_dismiss" -> {
                if (!site.isDirector(player)) return true;
                site.staff.remove(r.id);
                Entity e = entity(r);
                if (e != null) e.discard();
                site.log(r.fullName() + " was dismissed.");
                Gui.toast(player, r.fullName() + " was dismissed.", false);
                if (screen instanceof StaffCardScreen) Gui.close(player);
            }
            case "staff_assign" -> {
                String blocker = orderBlocker(site, r, player);
                if (blocker != null) Gui.toast(player, blocker, true);
                else Gui.openChild(player, new AssignScreen(site, r));
            }
            case "staff_follow" -> {
                if (r.role != StaffRole.SECURITY || orderBlocker(site, r, player) != null) return true;
                if (r.assignment.kind == Assignment.Kind.FOLLOW && player.getUUID().equals(r.assignment.leader)) {
                    r.assignment = new Assignment();
                    Gui.toast(player, r.name + " returns to their post.", false);
                } else {
                    r.assignment = new Assignment();
                    r.assignment.kind = Assignment.Kind.FOLLOW;
                    r.assignment.leader = player.getUUID();
                    Gui.toast(player, r.name + " is following you.", false);
                }
            }
            case "staff_terminal" -> {
                if (terminalBlocker(site, player) != null) return true;
                TerminalScreen t = new TerminalScreen(site, null);
                t.showStaff(r.id);
                Gui.open(player, t);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static Entity entity(StaffRecord r) {
        ServerLevel level = SiteManager.overworld();
        return level == null ? null : level.getEntity(r.id);
    }

    private static void refreshName(StaffRecord r) {
        if (entity(r) instanceof StaffEntity s) s.refreshName();
    }

    private StaffViews() {}
}
