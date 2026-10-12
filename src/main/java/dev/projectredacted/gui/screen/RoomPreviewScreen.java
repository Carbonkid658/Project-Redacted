package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Cost;
import net.minecraft.server.level.ServerPlayer;

/** The compact preview panel (GUI design §5.2): rotate, reroll, ghost on/off, then build or register. */
public class RoomPreviewScreen extends GuiScreen {
    static final String[] WALLS = {"North", "East", "South", "West"};

    private final Site site;
    private final DesignSession session;

    public RoomPreviewScreen(Site site, DesignSession session) {
        this.site = site;
        this.session = session;
    }

    @Override
    public String id() {
        return "room_preview";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || !site.canBuild(player)) return "You need build rights in a site to use this.";
        return RoomTypes.get(session.typeId) == null ? "No room type picked." : null;
    }

    @Override
    public View build(ServerPlayer p) {
        RoomType type = RoomTypes.get(session.typeId);
        String problem = RoomPlanner.validate(site, session, type);
        Cost cost = RoomPlanner.cost(type, session);
        String shortfall = Ui.shortfall(p, cost);
        View v = new View(type.name.toUpperCase() + " " + session.describeSize().replace(" x ", "×")).size("medium");
        v.add(El.row(El.text("Door wall: " + WALLS[session.rotation & 3]),
                El.text(problem == null ? "✔ Ready to build" : "✖ " + problem, problem == null ? "green" : "red")));
        v.add(El.text("Cost:", "gray"));
        v.add(El.cost(Ui.costLines(p, cost)));
        v.add(El.row(El.button("⟲ Rotate", "rotate"), El.button("Reroll", "reroll").tip("New palette and furniture layout."),
                El.button("Ghost: " + (session.previewing ? "ON" : "OFF"), "ghost")));
        v.add(El.row(El.button("Build", "build").style("confirm").disabled(problem != null ? problem : shortfall)
                        .tip("Pays the cost and starts construction.", "Rooms stay dormant until added to the Circuit Breaker."),
                El.button("Register hand-built", "register").disabled(problem)
                        .tip("You built this room yourself (manual override).", "Registers it as this room type for free.")));
        v.add(El.row(El.button("Change type", "change"), El.button("Cancel design", "cancel").style("danger").confirm("Throw away this design?")));
        v.footer("Keys while holding the device: R rotate · G reroll");
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "rotate" -> session.rotation = (session.rotation + 1) & 3;
            case "reroll" -> session.reroll();
            case "ghost" -> session.previewing = !session.previewing;
            case "build", "register" -> {
                Room room = RoomPlanner.commit(site, p, session, action.equals("register"));
                if (room != null) Gui.close(p);
            }
            case "change" -> {
                session.previewing = false;
                Gui.open(p, new RoomPickerScreen(site, session));
            }
            case "cancel" -> {
                session.reset();
                Gui.close(p);
            }
            default -> {}
        }
    }

    /** The R and G keybinds while holding the device in preview (no screen open). */
    public static void registerKeys() {
        Gui.registerGlobal("designer_rotate", (p, a) -> {
            DesignSession s = DesignSession.get(p);
            if (s.typeId == null || !p.getMainHandItem().is(dev.projectredacted.registry.ModItems.ROOM_DESIGNATOR)) return;
            s.rotation = (s.rotation + 1) & 3;
            s.previewing = true;
        });
        Gui.registerGlobal("designer_reroll", (p, a) -> {
            DesignSession s = DesignSession.get(p);
            if (s.typeId == null || !p.getMainHandItem().is(dev.projectredacted.registry.ModItems.ROOM_DESIGNATOR)) return;
            s.reroll();
            s.previewing = true;
        });
    }
}
