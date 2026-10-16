package dev.projectredacted.client.screen;

import com.google.gson.JsonObject;
import dev.projectredacted.net.ViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Opens, refreshes and closes Foundation screens as views arrive, and the room preview's R/G keys. */
public final class ClientGui {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ViewPayload.TYPE, (payload, context) -> show(context.client(), payload));
    }

    private static void show(Minecraft client, ViewPayload payload) {
        FoundationScreen open = FoundationScreen.current;
        if ("close".equals(payload.screen()) && payload.serial() == 0) {
            if (open != null) open.closeFromServer();
            return;
        }
        JsonObject view = FoundationScreen.parse(payload.json());
        if (open != null && open.serial() == payload.serial()) {
            open.update(payload.serial(), payload.screen(), view);
            return;
        }
        if (open != null) {
            // replaced by a new screen; the server already moved on, so don't report a close
            open.update(payload.serial(), payload.screen(), view);
            return;
        }
        if (payload.serial() == FoundationScreen.lastClosed) return;
        FoundationScreen screen = new FoundationScreen(payload.serial(), payload.screen(), view);
        FoundationScreen.current = screen;
        client.gui.setScreen(screen);
    }

    private ClientGui() {}
}
