package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.Skill;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Right-clicking a bunker survivor (GUI design §8.2): who they are, what they'd join as, and what it costs. */
public class RecruitScreen extends GuiScreen {
    private final StaffEntity survivor;

    public RecruitScreen(StaffEntity survivor) {
        this.survivor = survivor;
    }

    @Override
    public String id() {
        return "recruit";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (!Ui.near(player, survivor)) return "They're out of reach.";
        return survivor.isSurvivor() ? null : "They've already joined a site.";
    }

    @Override
    public View build(ServerPlayer p) {
        StaffRole role = survivor.role();
        Skill skill = survivor.survivorSkill();
        String name = survivor.survivorName();
        View v = new View(name + " · " + role.displayName).size("medium").face(Ui.staffSkin(role, survivor.skin()));
        List<String> lines = new ArrayList<>();
        lines.add(role.displayName + " · Foundation survivor");
        if (skill != null) lines.add("Skill: " + skill.displayName + " · " + skill.description);
        v.add(El.portrait(Ui.staffSkin(role, survivor.skin()), (name + " · " + role.displayName).toUpperCase(), skill != null ? "NAMED" : null, lines));
        v.add(El.text("\"" + survivor.recruitLine(p) + "\"", "amber"));
        v.add(El.sep());
        v.add(El.text("Joins as: " + role.title(role.baseClearance) + " (CLR " + role.baseClearance + ")"));
        Site site = SiteManager.directedBy(p.getUUID());
        if (site != null) {
            boolean bed = role == StaffRole.DCLASS || site.housedStaff() < site.beds;
            v.add(El.text(role == StaffRole.DCLASS ? "Sleeps in the Prison: no bed needed ✔" : "Beds at your site: " + site.housedStaff() + " / " + site.beds + (bed ? " ✔" : " ✖"),
                    bed ? "green" : "red"));
        } else {
            v.add(El.text("You don't direct a site yet.", "red"));
        }
        int cost = survivor.recruitCost();
        if (cost > 0) v.add(El.cost(Ui.costLines(p, Cost.of(Items.DIAMOND, cost))));
        else v.add(El.text("Cost: free", "green"));
        v.add(El.row(El.button("Not now", "close"),
                El.button(cost > 0 ? "Recruit (" + cost + " ◆)" : "Recruit", "recruit").style("confirm").disabled(survivor.recruitBlocker(p))));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args args) {
        switch (action) {
            case "close" -> Gui.close(p);
            case "recruit" -> {
                String blocker = survivor.recruitBlocker(p);
                if (blocker != null) Gui.toast(p, blocker, true);
                else if (survivor.recruit(p)) Gui.close(p);
            }
            default -> {}
        }
    }
}
