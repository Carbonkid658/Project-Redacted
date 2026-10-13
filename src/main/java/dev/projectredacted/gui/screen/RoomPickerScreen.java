package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Room Configuration (GUI design §5.1), after marking two corners with the Room Designator: categories, room
 * types (locked ones say why) and the cost for this exact area. Preview closes the screen and shows the ghost.
 */
public class RoomPickerScreen extends GuiScreen {
    private final Site site;
    private final DesignSession session;
    private String category;
    private String type;
    /** Auto-place mode (design §8.1): no corners; the mod finds a spot for the picked room type. */
    private final boolean auto;
    private int size;

    public RoomPickerScreen(Site site, DesignSession session) {
        this(site, session, false);
    }

    public RoomPickerScreen(Site site, DesignSession session, boolean auto) {
        this.site = site;
        this.session = session;
        this.auto = auto;
        RoomType current = RoomTypes.get(session.typeId);
        if (current != null) {
            category = current.category;
            type = current.id;
        }
    }

    @Override
    public String id() {
        return "room_picker";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || !site.canBuild(player)) return "You need build rights in a site to use this.";
        if (auto) return null;
        return session.cornerA == null || session.cornerB == null ? "Mark two corners first." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View(auto ? "ADD ROOM · AUTO-PLACE" : "ROOM CONFIGURATION · Area " + session.describeSize().replace(" x ", "×")).badge(Ui.clr(site, p));
        if (auto) {
            List<RoomType> suggested = dev.projectredacted.site.gen.AutoPlace.suggested(site);
            if (!suggested.isEmpty()) {
                List<El> chips = new ArrayList<>();
                chips.add(El.text("Suggested:", "gray"));
                for (RoomType t : suggested) chips.add(El.button(t.name, "suggest").arg("id", t.id));
                v.add(El.row(chips));
            }
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
        if (!auto) v.footerButton(El.button("Clear corners", "clear").confirm("Forget both corners and start over?"));
        else v.footer("Tip: mark two corners with the device to place a room by hand instead.");
        return v;
    }

    private List<El> detail(ServerPlayer p) {
        RoomType t = RoomTypes.get(type);
        if (t == null) return List.of(El.text("Pick a room type.", "gray"));
        List<El> out = new ArrayList<>();
        out.add(El.heading(t.name.toUpperCase()));
        out.add(El.text(t.description, "gray"));
        out.add(El.text("Size " + t.minSide + "–" + t.maxSide + " wide, " + t.minHeight + "–" + t.maxHeight + " tall"));
        out.add(El.text("Power " + t.powerDraw + (t.waterDraw > 0 ? " · Water " + t.waterDraw : "") + " · Clearance " + t.clearance));
        if (auto) {
            autoDetail(p, t, out);
            return out;
        }
        out.add(El.text("Cost for this area:"));
        out.add(El.cost(Ui.costLines(p, RoomPlanner.cost(t, session))));
        String problem = RoomPlanner.validate(site, session, t);
        out.add(El.text(problem == null ? "✔ Fits this area" : "✖ " + problem, problem == null ? "green" : "red"));
        out.add(El.button("Preview ▸", "preview").style("primary").disabled(RoomPlanner.lockedReason(site, t))
                .tip("Shows the room as ghost blocks.", "Use the device in the air for the build panel."));
        return out;
    }

    private void autoDetail(ServerPlayer p, RoomType t, List<El> out) {
        var sizes = dev.projectredacted.site.gen.PartKit.active().sizes(t.id);
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
            out.add(El.text("Room cost (hallways to reach it are extra):", "gray"));
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
                if (!auto || t == null || RoomPlanner.lockedReason(site, t) != null) return;
                var placing = dev.projectredacted.site.gen.AutoPlace.start(p, site, t.id, size);
                placing.search(p);
                Gui.open(p, new AutoPlaceScreen(placing).withParent(parent));
            }
            case "clear" -> {
                session.reset();
                Gui.close(p);
                Msg.actionBar(p, "Corners cleared.", ChatFormatting.YELLOW);
            }
            case "preview" -> {
                RoomType t = RoomTypes.get(type);
                if (t == null || RoomPlanner.lockedReason(site, t) != null) return;
                session.typeId = t.id;
                session.previewing = true;
                Gui.close(p);
            }
            default -> {}
        }
    }
}
