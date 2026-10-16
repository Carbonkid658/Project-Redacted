package dev.projectredacted.gui;

import net.minecraft.server.level.ServerPlayer;

/**
 * The server half of one screen: builds the {@link View} the client draws and applies the buttons the client
 * sends back. Nothing a client sends is trusted; every action re-checks permissions and costs.
 */
public abstract class GuiScreen {
    /** Where the Back button goes, or null for none. */
    public GuiScreen parent;
    int serial;

    /** Short name, used for the client's per-screen state (scroll positions, typed text). */
    public abstract String id();

    public abstract View build(ServerPlayer player);

    /** Applies a button press. The view is rebuilt and resent afterwards unless another screen was opened. */
    public void handle(ServerPlayer player, String action, Args args) {}

    /** Screens with timers or changing state are rebuilt once per second while open. */
    public boolean live() {
        return false;
    }

    /** Why this screen can't stay open (the block is gone, you walked away...), or null if it can. */
    public String invalid(ServerPlayer player) {
        return null;
    }

    public GuiScreen withParent(GuiScreen parent) {
        this.parent = parent;
        return this;
    }
}
