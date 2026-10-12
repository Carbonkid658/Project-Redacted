package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.AutoPlace;
import dev.projectredacted.site.gen.SitePart;
import dev.projectredacted.site.gen.SitePlan;
import dev.projectredacted.util.Cost;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-place build panel (design §8.1): the room type, its size, which spot is shown, the room and hallway
 * cost, and Build. Spots show in the world as a ghost outline.
 */
public class AutoPlaceScreen extends GuiScreen {
    private final AutoPlace auto;

    public AutoPlaceScreen(AutoPlace auto) {
        this.auto = auto;
    }

    @Override
    public String id() {
        return "auto_place";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(auto.site.id) != auto.site || !auto.site.canBuild(player)) return "You need build rights in a site to use this.";
        return auto.type() == null ? "Unknown room type." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        RoomType type = auto.type();
        View v = new View("ADD ROOM · " + type.name.toUpperCase()).size("medium").badge(Ui.clr(auto.site, p));
        List<SitePart> sizes = auto.sizes();
        if (sizes.size() > 1) {
            List<String> pairs = new ArrayList<>();
            for (int i = 0; i < sizes.size(); i++) {
                pairs.add(String.valueOf(i));
                pairs.add(AutoPlace.sizeLabel(sizes.get(i)));
            }
            v.add(El.chips("size", String.valueOf(auto.size), pairs.toArray(new String[0])));
        } else if (!sizes.isEmpty()) {
            v.add(El.text("Size " + AutoPlace.sizeLabel(sizes.get(0)), "gray"));
        }
        SitePlan plan = auto.current();
        if (plan == null) {
            v.add(El.text(auto.error != null ? auto.error : "Find a free spot joined to the facility. It shows as an outline in the world.", auto.error != null ? "red" : "gray"));
            v.add(El.row(El.button("Find a spot", "find").style("primary"), El.button("Near me", "near").tip("Looks for spots closest to where you stand.")));
        } else {
            int halls = auto.hallways(plan);
            v.add(El.text("Spot " + (auto.index + 1) + " of " + auto.spots.size() + (halls > 0 ? " · " + halls + " hallway part(s) to reach it" : " · joins the facility directly")
                    + (auto.nearMe ? " · nearest to you" : ""), "white"));
            if (plan.overwrites > 0) v.add(El.text(plan.overwrites + " blocks you built are in the way and would be replaced.", "red"));
            Cost cost = auto.cost(plan);
            v.add(El.text("Cost (room" + (halls > 0 ? " and hallways" : "") + "):", "gray"));
            v.add(El.cost(Ui.costLines(p, cost)));
            String problem = auto.problem();
            String blocked = problem != null ? problem : p.isCreative() ? null : Ui.shortfall(p, cost);
            var build = El.button("Build", "build").style("confirm").disabled(blocked)
                    .tip("Pays the cost and starts construction at the normal speed.");
            if (plan.overwrites > 0) build.style("danger").confirm("Replace " + plan.overwrites + " player-built blocks to build here?");
            v.add(El.row(build, El.button("Next spot", "next"), El.button("Near me", "near"), El.button("New spots", "reroll")));
            v.add(El.row(El.button("Look at it", "look").tip("Closes this panel so you can see the outline."),
                    El.button("Cancel", "cancel").style("danger")));
        }
        v.footer("Keys while holding the device: N or R next spot · G new spots");
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "size" -> {
                auto.size = a.num("id", auto.size);
                auto.search(p);
            }
            case "find" -> {
                auto.nearMe = false;
                auto.search(p);
            }
            case "near" -> {
                auto.nearMe = true;
                auto.search(p);
            }
            case "next" -> auto.next(p);
            case "reroll" -> auto.reroll(p);
            case "look" -> Gui.close(p);
            case "cancel" -> {
                AutoPlace.stop(p);
                Gui.close(p);
            }
            case "build" -> {
                String err = auto.build(p);
                if (err != null) Gui.toast(p, err, true);
                else Gui.close(p);
            }
            default -> {}
        }
    }
}
