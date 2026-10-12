package dev.projectredacted.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.net.ActionPayload;
import dev.projectredacted.net.ViewPayload;
import dev.projectredacted.util.Msg;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Which screen each player has open, and the round trip: open sends a view, a click comes back as an action,
 * the server applies it and resends the view so the screen refreshes in place (GUI design §1).
 */
public final class Gui {
    private static final Map<UUID, GuiScreen> OPEN = new HashMap<>();
    private static final Map<UUID, String> TOASTS = new HashMap<>();
    /** Actions that work with no screen open, like the room preview keybinds. */
    private static final Map<String, BiConsumer<ServerPlayer, Args>> GLOBAL = new HashMap<>();
    private static int nextSerial = 1;
    private static int ticks;

    public static void open(ServerPlayer player, GuiScreen screen) {
        if (!ServerPlayNetworking.canSend(player, ViewPayload.TYPE)) {
            Msg.send(player, "This needs Project [REDACTED] installed on your game too.", ChatFormatting.RED);
            return;
        }
        player.closeContainer();
        screen.serial = nextSerial++;
        OPEN.put(player.getUUID(), screen);
        send(player, screen);
    }

    /** Opens a screen whose Back button returns to the one that's open now. */
    public static void openChild(ServerPlayer player, GuiScreen screen) {
        screen.parent = current(player);
        open(player, screen);
    }

    public static GuiScreen current(ServerPlayer player) {
        return OPEN.get(player.getUUID());
    }

    public static void close(ServerPlayer player) {
        if (OPEN.remove(player.getUUID()) != null && ServerPlayNetworking.canSend(player, ViewPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ViewPayload(0, "close", "{}"));
        }
    }

    /** Resends the open screen's view, e.g. after something changed elsewhere. */
    public static void refresh(ServerPlayer player) {
        GuiScreen s = current(player);
        if (s != null) send(player, s);
    }

    /** A short popup in the corner of the open screen, or the action bar when none is open. */
    public static void toast(ServerPlayer player, String text, boolean error) {
        if (current(player) == null) {
            Msg.actionBar(player, text, error ? ChatFormatting.RED : ChatFormatting.GREEN);
            return;
        }
        TOASTS.put(player.getUUID(), (error ? "!" : "") + text);
    }

    public static void registerGlobal(String action, BiConsumer<ServerPlayer, Args> handler) {
        GLOBAL.put(action, handler);
    }

    private static void send(ServerPlayer player, GuiScreen screen) {
        JsonObject json;
        try {
            json = screen.build(player).json();
        } catch (RuntimeException e) {
            ProjectRedacted.LOG.error("Couldn't build the {} screen", screen.id(), e);
            close(player);
            Msg.send(player, "That screen failed to open. Details are in the server log.", ChatFormatting.RED);
            return;
        }
        String toast = TOASTS.remove(player.getUUID());
        if (toast != null) {
            json.addProperty("toast", toast.startsWith("!") ? toast.substring(1) : toast);
            json.addProperty("toastError", toast.startsWith("!"));
        }
        json.addProperty("hasParent", screen.parent != null);
        ServerPlayNetworking.send(player, new ViewPayload(screen.serial, screen.id(), json.toString()));
    }

    public static void onAction(ServerPlayer player, ActionPayload payload) {
        Args args;
        try {
            JsonObject json = JsonParser.parseString(payload.json()).getAsJsonObject();
            args = new Args(json);
        } catch (RuntimeException e) {
            args = new Args(null);
        }
        if (payload.serial() == 0) {
            BiConsumer<ServerPlayer, Args> global = GLOBAL.get(payload.action());
            if (global != null) global.accept(player, args);
            return;
        }
        GuiScreen screen = current(player);
        if (screen == null || screen.serial != payload.serial()) return;
        switch (payload.action()) {
            case "_close" -> {
                OPEN.remove(player.getUUID());
                return;
            }
            case "_back" -> {
                if (screen.parent != null) open(player, screen.parent);
                else close(player);
                return;
            }
            default -> {}
        }
        String invalid = screen.invalid(player);
        if (invalid != null) {
            close(player);
            Msg.actionBar(player, invalid, ChatFormatting.RED);
            return;
        }
        try {
            screen.handle(player, payload.action(), args);
        } catch (RuntimeException e) {
            ProjectRedacted.LOG.error("Screen {} failed on action {}", screen.id(), payload.action(), e);
            toast(player, "Something went wrong. Details are in the server log.", true);
        }
        if (current(player) == screen) send(player, screen);
    }

    public static void tick(MinecraftServer server) {
        if (++ticks % 20 != 0 || OPEN.isEmpty()) return;
        for (Map.Entry<UUID, GuiScreen> e : Map.copyOf(OPEN).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                OPEN.remove(e.getKey());
                continue;
            }
            GuiScreen screen = e.getValue();
            String invalid = screen.invalid(player);
            if (invalid != null) {
                close(player);
                Msg.actionBar(player, invalid, ChatFormatting.RED);
            } else if (screen.live()) {
                send(player, screen);
            }
        }
    }

    public static void clear() {
        OPEN.clear();
        TOASTS.clear();
    }

    private Gui() {}
}
