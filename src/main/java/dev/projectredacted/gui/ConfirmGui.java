package dev.projectredacted.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** A yes/no question. */
public final class ConfirmGui {
    public static void open(ServerPlayer player, String title, String question, Runnable yes, Runnable no) {
        ChestGui gui = new ChestGui(title, 3);
        gui.set(13, ChestGui.icon(Items.PAPER, title, ChatFormatting.GOLD, wrap(question)));
        gui.button(11, ChestGui.icon(Items.CONCRETE.lime(), "Yes", ChatFormatting.GREEN), (p, r) -> {
            p.closeContainer();
            yes.run();
        });
        gui.button(15, ChestGui.icon(Items.CONCRETE.red(), "No", ChatFormatting.RED), (p, r) -> {
            p.closeContainer();
            if (no != null) no.run();
        });
        gui.open(player);
    }

    public static String[] wrap(String text) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() > 38) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    private ConfirmGui() {}
}
