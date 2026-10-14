package dev.projectredacted.net;

import dev.projectredacted.gui.Gui;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers the two GUI packets. Screens are drawn by the client; all logic stays on the server. */
public final class ModNetwork {
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ViewPayload.TYPE, ViewPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> Gui.onAction(context.player(), payload));
    }

    private ModNetwork() {}
}
