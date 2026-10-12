package dev.projectredacted.gui;

import dev.projectredacted.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side menus built from a chest screen, where every item is a button. This needs no client code, so the
 * terminal, breaker, clipboard and shops all work the same in single player and on servers.
 */
public class ChestGui {
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, boolean rightClick);
    }

    private final String title;
    private final int rows;
    private final SimpleContainer container;
    private final Map<Integer, Action> actions = new HashMap<>();

    public ChestGui(String title, int rows) {
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
        this.container = new SimpleContainer(this.rows * 9);
    }

    public int size() {
        return rows * 9;
    }

    public ChestGui set(int slot, ItemStack icon) {
        if (slot >= 0 && slot < size()) container.setItem(slot, icon);
        return this;
    }

    public ChestGui button(int slot, ItemStack icon, Action action) {
        set(slot, icon);
        if (slot >= 0 && slot < size()) actions.put(slot, action);
        return this;
    }

    /** Fills empty slots of the bottom row with glass and adds a Back button in the corner. */
    public ChestGui footer(Action back) {
        int start = size() - 9;
        for (int i = start; i < size(); i++) if (container.getItem(i).isEmpty()) set(i, filler());
        if (back != null) button(start, icon(Items.ARROW, "Back", ChatFormatting.GRAY), back);
        return this;
    }

    public void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new Menu(id, inv, this), Component.literal(title)));
    }

    /** Opens a menu on the next tick; safe to call from inside a click handler. */
    public static void later(Runnable open) {
        Scheduler.next(open);
    }

    public static ItemStack icon(ItemLike item, String name, ChatFormatting color, String... lore) {
        List<String> lines = new ArrayList<>(List.of(lore));
        return icon(item, name, color, lines);
    }

    public static ItemStack icon(ItemLike item, String name, ChatFormatting color, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(color)));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Component.literal(l).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    public static ItemStack filler() {
        return icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY);
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static class Menu extends ChestMenu {
        private final ChestGui gui;

        Menu(int id, Inventory inventory, ChestGui gui) {
            super(type(gui.rows), id, inventory, gui.container, gui.rows);
            this.gui = gui;
        }

        @Override
        public void clicked(int slot, int button, ClickType clickType, Player player) {
            if (slot >= 0 && slot < gui.size() && player instanceof ServerPlayer sp) {
                Action action = gui.actions.get(slot);
                if (action != null && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                    boolean right = button == 1;
                    Scheduler.next(() -> action.run(sp, right));
                }
            }
            sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
