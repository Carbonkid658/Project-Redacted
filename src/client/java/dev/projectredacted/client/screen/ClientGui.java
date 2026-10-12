package dev.projectredacted.client.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.net.ActionPayload;
import dev.projectredacted.net.ViewPayload;
import dev.projectredacted.registry.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Opens, refreshes and closes Foundation screens as views arrive, and the room preview's R/G keys. */
public final class ClientGui {
    private static KeyMapping rotate, reroll;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ViewPayload.TYPE, (payload, context) -> show(context.client(), payload));

        KeyMapping.Category category = KeyMapping.Category.register(ProjectRedacted.id("controls"));
        rotate = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projectredacted.rotate", InputConstants.KEY_R, category));
        reroll = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.projectredacted.reroll", InputConstants.KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(ClientGui::tick);
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

    private static void tick(Minecraft client) {
        while (rotate.consumeClick()) designer(client, "designer_rotate");
        while (reroll.consumeClick()) designer(client, "designer_reroll");
    }

    private static void designer(Minecraft client, String action) {
        if (client.player == null || FoundationScreen.current != null) return;
        if (!client.player.getMainHandItem().is(ModItems.ROOM_DESIGNATOR) && !client.player.getOffhandItem().is(ModItems.ROOM_DESIGNATOR)) return;
        ClientPlayNetworking.send(new ActionPayload(0, action, "{}"));
    }

    private ClientGui() {}
}
