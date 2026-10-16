package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRecord;
import net.minecraft.server.level.ServerPlayer;

/** Right-clicking a staff member (GUI design §8.1). Strangers get a short read-only card. */
public class StaffCardScreen extends GuiScreen {
    private final StaffEntity entity;

    public StaffCardScreen(StaffEntity entity) {
        this.entity = entity;
    }

    @Override
    public String id() {
        return "staff_card";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (!Ui.near(player, entity)) return "They're out of reach.";
        return entity.record() == null ? "They no longer work at a site." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        Site site = entity.site();
        StaffRecord r = entity.record();
        View v = new View(r.fullName()).size("medium").face(Ui.staffSkin(r.role, r.skin)).badge(Ui.clr(site, p));
        if (!site.isMember(p)) {
            v.add(El.portrait(Ui.staffSkin(r.role, r.skin), r.fullName().toUpperCase(), r.named ? "NAMED" : null,
                    java.util.List.of(r.role.displayName, "Works for " + site.directorName + "'s site, " + site.name() + ".")));
            return v;
        }
        return v.addAll(StaffViews.card(site, r, p, StaffViews.Buttons.ORDERS, StaffViews.Buttons.TERMINAL));
    }

    @Override
    public void handle(ServerPlayer p, String action, Args args) {
        Site site = entity.site();
        StaffRecord r = entity.record();
        if (site != null && r != null && site.isMember(p) && SiteManager.byId(site.id) == site) StaffViews.handle(this, site, r, p, action);
    }
}
