package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** A room's panel, opened from its key block (v4 §5.8): status, integrity, power and its name. */
public class RoomPanelScreen extends GuiScreen {
    private final Site site;
    private final int number;
    private final BlockPos at;

    public RoomPanelScreen(Site site, int number, BlockPos at) {
        this.site = site;
        this.number = number;
        this.at = at;
    }

    @Override
    public String id() {
        return "room_panel";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || site.room(number) == null) return "This is no longer a room.";
        if (!Ui.near(player, at)) return "You stepped away.";
        return null;
    }

    @Override
    public View build(ServerPlayer p) {
        Room r = site.room(number);
        RoomType t = r.typeDef();
        boolean canEdit = site.canBuild(p);
        View v = new View(r.displayName()).size("medium").badge(Ui.clr(site, p));
        v.add(El.text(r.label().toUpperCase() + " · " + (t == null ? r.type : t.name.toUpperCase()), "gray"));
        v.add(El.text(TerminalScreen.status(r), TerminalScreen.statusColor(r)));
        v.add(El.need("Integrity", r.integrity));
        if (t != null && t.powerDraw > 0) {
            int used = 0;
            for (Room o : site.rooms) if (o.connected) used++;
            El toggle = El.toggle("Power (draws " + t.powerDraw + ")", r.connected, "power");
            if (!canEdit) toggle.set("disabled", "You need build rights.");
            else if (!r.connected && used >= site.connectCapacity) toggle.set("disabled", "No free breaker slots. Build another Circuit Breaker room.");
            v.add(toggle);
        }
        v.add(El.input("rename", "New name", El.button("Rename", "rename").disabled(canEdit ? null : "You need build rights.")));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        Room r = site.room(number);
        if (r == null || !site.canBuild(p)) return;
        RoomType t = r.typeDef();
        switch (action) {
            case "power" -> {
                if (t == null || t.powerDraw <= 0) return;
                int used = 0;
                for (Room o : site.rooms) if (o.connected) used++;
                if (!r.connected && used >= site.connectCapacity) return;
                r.connected = !r.connected;
            }
            case "rename" -> {
                String name = a.text();
                r.name = name.length() > 32 ? name.substring(0, 32) : name;
                Gui.toast(p, name.isEmpty() ? "Name cleared." : "Renamed to " + r.name + ".", false);
            }
            default -> {}
        }
    }
}
