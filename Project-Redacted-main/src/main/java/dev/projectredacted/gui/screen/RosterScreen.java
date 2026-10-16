package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.StaffRecord;
import dev.projectredacted.staff.StaffRole;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** The clipboard used in the air (GUI design §7.1): the roster, with remote task assignment. */
public class RosterScreen extends GuiScreen {
    private final Site site;
    private UUID selected;

    public RosterScreen(Site site) {
        this.site = site;
    }

    @Override
    public String id() {
        return "roster";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String invalid(ServerPlayer player) {
        return SiteManager.byId(site.id) == site && site.isMember(player) ? null : "You no longer belong to this site.";
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("CLIPBOARD · ROSTER · " + site.name()).badge(Ui.clr(site, p));
        El.ListEl rows = El.list("roster", "select").searchable().empty("No staff yet. Recruit survivors from Foundation Bunkers.").fill();
        for (StaffRecord s : site.staff.values()) {
            boolean warn = s.bedIndex < 0 && s.role != StaffRole.DCLASS || s.morale < 30;
            rows.add(s.id.toString(), s.fullName()).icon(s.role.icon()).color(s.named ? "gold" : "white")
                    .sub(s.role.displayName + " · CLR " + s.clearance, "gray").badge(warn ? "⚠" : null, "red").selected(s.id.equals(selected)).search(s.role.displayName);
        }
        StaffRecord s = selected == null ? null : site.staff.get(selected);
        List<El> right = s == null ? List.of(El.text("Pick someone to see their card and give them a task.", "gray"))
                : StaffViews.card(site, s, p, StaffViews.Buttons.ORDERS);
        v.add(El.cols(El.col(0.46, List.of(rows)), El.col(0.54, right)));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        if (action.equals("select")) {
            selected = TerminalScreen.parseUuid(a.str("id"));
            return;
        }
        StaffRecord s = selected == null ? null : site.staff.get(selected);
        if (s != null) StaffViews.handle(this, site, s, p, action);
    }
}
