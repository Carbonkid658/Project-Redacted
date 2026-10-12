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

    public RoomPickerScreen(Site site, DesignSession session) {
        this.site = site;
        this.session = session;
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
        return session.cornerA == null || session.cornerB == null ? "Mark two corners first." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("ROOM CONFIGURATION · Area " + session.describeSize().replace(" x ", "×")).badge(Ui.clr(site, p));
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
        v.footerButton(El.button("Clear corners", "clear").confirm("Forget both corners and start over?"));
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
        out.add(El.text("Cost for this area:"));
        out.add(El.cost(Ui.costLines(p, RoomPlanner.cost(t, session))));
        String problem = RoomPlanner.validate(site, session, t);
        out.add(El.text(problem == null ? "✔ Fits this area" : "✖ " + problem, problem == null ? "green" : "red"));
        out.add(El.button("Preview ▸", "preview").style("primary").disabled(RoomPlanner.lockedReason(site, t))
                .tip("Shows the room as ghost blocks.", "Use the device in the air for the build panel."));
        return out;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "category" -> {
                category = a.str("id");
                type = null;
            }
            case "type" -> type = a.str("id");
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
