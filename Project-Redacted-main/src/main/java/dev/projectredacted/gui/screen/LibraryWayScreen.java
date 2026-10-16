package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.npc.Library;
import dev.projectredacted.npc.TraderEntity;
import net.minecraft.server.level.ServerPlayer;

/** A Way to the Wanderers' Library (GUI design §9.3): asks first, so nobody steps through by accident. */
public class LibraryWayScreen extends GuiScreen {
    private final TraderEntity way;

    public LibraryWayScreen(TraderEntity way) {
        this.way = way;
    }

    @Override
    public String id() {
        return "library_way";
    }

    @Override
    public String invalid(ServerPlayer player) {
        return Ui.near(player, way) ? null : "The Way has faded.";
    }

    @Override
    public View build(ServerPlayer p) {
        return new View("A WAY TO THE LIBRARY").size("small")
                .add(El.text("The air folds in on itself. Somewhere beyond, pages turn and someone hums. A Way to the Wanderers' Library.", "purple"))
                .add(El.row(El.button("Leave it", "leave"), El.button("Step through", "enter").style("primary")));
    }

    @Override
    public void handle(ServerPlayer p, String action, Args args) {
        Gui.close(p);
        if (action.equals("enter") && way.isAlive()) Library.enter(p);
    }
}
