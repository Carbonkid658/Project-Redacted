package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.Expansion;
import net.minecraft.server.level.ServerPlayer;

/** Auto-expand priorities (design §8.2): favor a room type, leave it normal, or never build it automatically. */
public class ExpandPriorityScreen extends GuiScreen {
    private final Site site;

    public ExpandPriorityScreen(Site site) {
        this.site = site;
    }

    @Override
    public String id() {
        return "expand_priorities";
    }

    @Override
    public String invalid(ServerPlayer player) {
        return SiteManager.byId(site.id) != site || !site.isDirector(player) ? "Only the Site Director can change this." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("AUTO-EXPAND PRIORITIES").size("medium");
        v.add(El.text("Favored rooms are ordered before others once beds, power and water are covered. Never stops a room being built automatically.", "gray"));
        for (RoomType t : RoomTypes.all()) {
            if (t.id.equals(RoomTypes.ENTRANCE) || t.id.equals(RoomTypes.DIRECTORS_OFFICE)) continue;
            v.add(El.row(El.text(t.name), El.chips("priority:" + t.id, Expansion.priority(site, t.id), "normal", "Normal", "favor", "Favor", "never", "Never")));
        }
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        if (!action.startsWith("priority:") || !site.isDirector(p)) return;
        String type = action.substring("priority:".length());
        if (RoomTypes.get(type) == null) return;
        Expansion.setPriority(site, type, a.str("id", "normal"));
    }
}
