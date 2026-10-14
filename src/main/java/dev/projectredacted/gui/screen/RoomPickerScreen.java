package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Add Room (site generation v4 §5.8): categories and room types (locked ones say why). The mod finds a spot
 * joined to the facility in the room's area and shows it as an outline. Rooms by hand need no screen: wall a
 * space in and place the room's key block inside.
 */
public class RoomPickerScreen extends GuiScreen {
    private final Site site;
    private String category;
    private String type;
    private int size;

    public RoomPickerScreen(Site site) {
        this.site = site;
    }

    @Override
    public String id() {
        return "room_picker";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || !site.canBuild(player)) return "You need build rights in a site to use this.";
        return null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("ADD ROOM · AUTO-PLACE").badge(Ui.clr(site, p));
        List<RoomType> suggested = dev.projectredacted.site.gen.AutoPlace.suggested(site);
        if (!suggested.isEmpty()) {
            List<El> chips = new ArrayList<>();
            chips.add(El.text("Suggested:", "gray"));
            for (RoomType t : suggested) chips.add(El.button(t.name, "suggest").arg("id", t.id));
            v.add(El.row(chips));
        }
        El.ListEl cats = El.list("categories", "category").fill();
        for (String c : RoomTypes.CATEGORIES) {
            List<RoomType> types = RoomTypes.inCategory(c);
            if (types.isEmpty()) continue;
            if (category == null) category = c;
            cats.add(c, c).icon(types.get(0).iconItem()).color(Ui.categoryColor(c)).selected(c.equals(category));
        }
        El.ListEl types = El.list("types", "type").fill();
        for (RoomType t : RoomTypes.inCategory(category == null ? "" : category)) {
            String locked = RoomPlanner.lockedReason(site, t);
            El.Row row = types.add(t.id, t.name).icon(t.iconItem()).selected(t.id.equals(type));
            if (locked != null) row.dim(true).badge("LOCKED", "gray").sub(locked, "gray").tip(locked);
        }
        v.add(El.cols(El.col(0.26, List.of(cats)), El.col(0.32, List.of(types)), El.col(0.42, detail(p))));
        v.footer("Tip: to build a room by hand, wall it in (floor and roof too) and place its key block inside.");
        return v;
    }

    private List<El> detail(ServerPlayer p) {
        RoomType t = RoomTypes.get(type);
        if (t == null) return List.of(El.text("Pick a room type.", "gray"));
        List<El> out = new ArrayList<>();
        out.add(El.heading(t.name.toUpperCase()));
        out.add(El.text(t.description, "gray"));
        out.add(El.text("Power " + t.powerDraw + (t.waterDraw > 0 ? " · Water " + t.waterDraw : "") + " · Clearance " + t.clearance));
        String key = dev.projectredacted.site.gen.AutoPlace.keyStatus(p, site, t);
        if (key != null) out.add(El.text(key, key.startsWith("Missing") ? "red" : "green"));
        autoDetail(p, t, out);
        return out;
    }

    private void autoDetail(ServerPlayer p, RoomType t, List<El> out) {
        var sizes = dev.projectredacted.site.gen.PartKit.active().sizes(t.id, dev.projectredacted.site.gen.AutoPlace.wantedScps((net.minecraft.server.level.ServerLevel) p.level(), site));
        if (sizes.size() > 1) {
            List<String> pairs = new ArrayList<>();
            for (int i = 0; i < sizes.size(); i++) {
                pairs.add(String.valueOf(i));
                pairs.add(dev.projectredacted.site.gen.AutoPlace.sizeLabel(sizes.get(i)));
            }
            out.add(El.text("Size:", "gray"));
            out.add(El.chips("size", String.valueOf(Math.min(size, sizes.size() - 1)), pairs.toArray(new String[0])));
        } else if (!sizes.isEmpty()) {
            out.add(El.text("Size " + dev.projectredacted.site.gen.AutoPlace.sizeLabel(sizes.get(0)), "gray"));
        }
        if (!sizes.isEmpty()) {
            out.add(El.text("Cost (hallways to reach it are extra):", "gray"));
            out.add(El.cost(Ui.costLines(p, dev.projectredacted.site.gen.AutoPlace.partCost(sizes.get(Math.min(size, sizes.size() - 1))))));
        }
        out.add(El.button("Find a spot ▸", "find").style("primary").disabled(RoomPlanner.lockedReason(site, t))
                .tip("Finds a free spot joined to the facility and shows it as an outline.", "Builds at the normal construction speed."));
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "category" -> {
                category = a.str("id");
                type = null;
                size = 0;
            }
            case "type" -> {
                type = a.str("id");
                size = 0;
            }
            case "suggest" -> {
                RoomType t = RoomTypes.get(a.str("id"));
                if (t != null) {
                    type = t.id;
                    category = t.category;
                    size = 0;
                }
            }
            case "size" -> size = Math.max(0, a.num("id", 0));
            case "find" -> {
                RoomType t = RoomTypes.get(type);
                if (t == null || RoomPlanner.lockedReason(site, t) != null) return;
                var placing = dev.projectredacted.site.gen.AutoPlace.start(p, site, t.id, size);
                placing.search(p);
                Gui.open(p, new AutoPlaceScreen(placing).withParent(parent));
            }
            default -> {}
        }
    }
}
