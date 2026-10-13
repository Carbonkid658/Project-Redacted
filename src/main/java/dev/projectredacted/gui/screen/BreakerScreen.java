package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The Circuit Breaker (GUI design §6): one switch per room. Hovering a switch previews the power bar after
 * flipping it, so you can see if you'd go over supply.
 */
public class BreakerScreen extends GuiScreen {
    private final Site site;
    private final BlockPos at;
    private String filter = "All";

    public BreakerScreen(Site site, BlockPos at) {
        this.site = site;
        this.at = at;
    }

    @Override
    public String id() {
        return "breaker";
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || !site.canAssign(player)) return "ACCESS DENIED.";
        if (!Ui.near(player, at) || !player.level().getBlockState(at).is(ModBlocks.CIRCUIT_BREAKER)) return "You stepped away from the breaker.";
        return null;
    }

    private int used() {
        int n = 0;
        for (Room r : site.rooms) if (r.connected) n++;
        return n;
    }

    private static String problem(Room r, RoomType t) {
        if (!r.built) return "Under construction";
        if (r.integrity <= 0) return "Damaged: needs repair";
        if (r.connected && !r.poweredNow) return "Not enough power";
        if (t.waterDraw > 0 && r.connected && !r.wateredNow) return "No water";
        return null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("CIRCUIT BREAKER · " + site.name()).badge(Ui.clr(site, p));
        int used = used();
        v.add(El.row(El.usage("Power", site.powerDemand, site.powerSupply).id("power"),
                El.usage("Slots", used, site.connectCapacity),
                El.usage("Water", site.waterDemand, site.waterSupply)));
        v.add(El.chips("filter", filter, List.of("All", "Connected", "Off", "Problems")));
        El.ListEl list = El.list("breaker", "").searchable().empty("No rooms that draw power.").fill();
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t == null || t.powerDraw <= 0) continue;
            String problem = problem(r, t);
            boolean show = switch (filter) {
                case "Connected" -> r.connected;
                case "Off" -> !r.connected;
                case "Problems" -> problem != null;
                default -> true;
            };
            if (!show) continue;
            El toggle = El.toggle("", r.connected, "toggle").set("arg", r.number);
            int after = site.powerDemand + (r.connected ? (r.poweredNow ? -t.powerDraw : 0) : t.powerDraw);
            toggle.set("preview", El.text("").id("power").set("value", after));
            if (!r.connected && used >= site.connectCapacity) toggle.set("disabled", "All breaker slots are used. Build another Circuit Breaker room.");
            else if (!r.built) toggle.set("disabled", "Under construction.");
            list.add(String.valueOf(r.number), (r.poweredNow ? "⚡ " : "") + r.displayName()).icon(t.iconItem())
                    .sub("draws " + t.powerDraw + (t.waterDraw > 0 ? " · water " + t.waterDraw : "") + (problem != null ? " · ⚠ " + problem : ""),
                            problem != null ? "amber" : "gray")
                    .right(toggle);
        }
        v.add(list);
        if (!site.mainframeOnline()) v.banner("⚠ MAINFRAME OFFLINE: nothing is powered.", "red");
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "filter" -> filter = a.str("id", "All");
            case "toggle" -> {
                Room r = site.room(a.num("arg", -1));
                RoomType t = r == null ? null : r.typeDef();
                if (t == null || t.powerDraw <= 0 || !r.built) return;
                if (!r.connected && used() >= site.connectCapacity) {
                    Gui.toast(p, "No free breaker slots. Build another Circuit Breaker room.", true);
                    return;
                }
                r.connected = !r.connected;
            }
            default -> {}
        }
    }
}
