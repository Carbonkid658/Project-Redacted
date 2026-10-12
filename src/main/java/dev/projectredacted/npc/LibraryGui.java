package dev.projectredacted.npc;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.gui.ChestGui;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Librarian takes SCPs off your hands. More dangerous SCPs earn better rewards: Library Tomes (which cut
 * upgrade costs) and Dollars. Dealing with the Hand never protects you from their raids.
 */
public final class LibraryGui {
    public static void open(ServerPlayer player) {
        ChestGui gui = new ChestGui("The Librarian", 3);
        int slot = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && slot < 18; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ModTags.SCP_ITEMS)) continue;
            int invSlot = i;
            ScpDefinition def = ScpApi.forItem(stack.getItem());
            int weight = Math.max(1, ScpApi.raidWeight(def));
            ItemStack icon = ChestGui.icon(stack.getItem(), "Give " + stack.getHoverName().getString(), ChatFormatting.LIGHT_PURPLE,
                    "Reward: " + weight + " Library Tome(s) and $" + (5 * weight));
            gui.button(slot++, icon, (p, r) -> {
                ItemStack s = p.getInventory().getItem(invSlot);
                if (!s.is(ModTags.SCP_ITEMS)) return;
                s.shrink(1);
                reward(p, weight);
                ChestGui.later(() -> open(p));
            });
        }
        // Entity SCPs the player brought along.
        for (Entity e : player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(8), x -> x.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES))) {
            if (slot >= 18) break;
            ScpDefinition def = ScpApi.forEntity(e.getType());
            int weight = Math.max(1, ScpApi.raidWeight(def)) * 2;
            gui.button(slot++, ChestGui.icon(Items.LEAD, "Hand over " + (def != null ? def.number : e.getName().getString()), ChatFormatting.LIGHT_PURPLE,
                    "Reward: " + weight + " Library Tome(s) and $" + (5 * weight)), (p, r) -> {
                if (!e.isAlive()) return;
                e.discard();
                reward(p, weight);
                ChestGui.later(() -> open(p));
            });
        }
        if (slot == 0) {
            gui.set(4, ChestGui.icon(Items.BOOK, "\"Bring us anomalies.\"", ChatFormatting.DARK_PURPLE,
                    "The Library values SCPs, and pays better", "for the dangerous ones."));
        }
        gui.button(22, ChestGui.icon(Items.ENDER_PEARL, "Return to the Overworld", ChatFormatting.AQUA), (p, r) -> {
            p.closeContainer();
            Library.leave(p);
        });
        gui.footer(null);
        gui.open(player);
    }

    private static void reward(ServerPlayer player, int weight) {
        Cost.give(player, new ItemStack(ModItems.LIBRARY_TOME, weight));
        Cost.give(player, new ItemStack(ModItems.DOLLAR, 5 * weight));
        player.sendSystemMessage(Component.literal("\"The Library thanks you.\"").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private LibraryGui() {}
}
