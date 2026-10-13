package dev.projectredacted.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class Msg {
    public static void send(Player player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static void actionBar(Player player, String text, ChatFormatting color) {
        player.sendOverlayMessage(Component.literal(text).withStyle(color));
    }

    private Msg() {}
}
