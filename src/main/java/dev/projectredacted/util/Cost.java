package dev.projectredacted.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A bundle of items to pay. Used for rooms, upgrades and shops. */
public final class Cost {
    public final Map<Item, Integer> items = new LinkedHashMap<>();

    public static Cost of(Object... pairs) {
        Cost cost = new Cost();
        for (int i = 0; i + 1 < pairs.length; i += 2) cost.add((Item) pairs[i], (Integer) pairs[i + 1]);
        return cost;
    }

    public static Cost parse(Map<String, Integer> raw) {
        Cost cost = new Cost();
        if (raw == null) return cost;
        raw.forEach((id, count) -> {
            Identifier rl = Identifier.tryParse(id);
            if (rl != null) BuiltInRegistries.ITEM.getOptional(rl).ifPresent(item -> cost.add(item, count));
        });
        return cost;
    }

    public Cost add(Item item, int count) {
        if (count > 0) items.merge(item, count, Integer::sum);
        return this;
    }

    public Cost scaled(double factor) {
        Cost cost = new Cost();
        items.forEach((item, count) -> cost.add(item, Math.max(1, (int) Math.ceil(count * factor))));
        return cost;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static int count(Player player, Item item) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    public boolean canAfford(Player player) {
        if (player.isCreative()) return true;
        for (Map.Entry<Item, Integer> e : items.entrySet()) {
            if (count(player, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    /** Takes the items if the player can afford all of them. Creative players pay nothing. */
    public boolean pay(Player player) {
        if (!canAfford(player)) return false;
        if (player.isCreative()) return true;
        for (Map.Entry<Item, Integer> e : items.entrySet()) take(player, e.getKey(), e.getValue());
        return true;
    }

    public static void take(Player player, Item item, int amount) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int n = Math.min(amount, stack.getCount());
                stack.shrink(n);
                amount -= n;
            }
        }
        inv.setChanged();
    }

    public static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        items.forEach((item, count) -> lines.add(count + "x " + new ItemStack(item).getHoverName().getString()));
        return lines;
    }
}
