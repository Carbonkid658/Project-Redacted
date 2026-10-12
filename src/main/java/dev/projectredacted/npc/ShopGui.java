package dev.projectredacted.npc;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.gui.ChestGui;
import dev.projectredacted.registry.ModComponents;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Time;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shops priced in Dollars. */
public final class ShopGui {
    public record Offer(ItemStack result, int price, int stock) {}

    public static List<Offer> offers(TraderEntity trader) {
        List<Offer> list = new ArrayList<>();
        switch (trader.kind()) {
            case GOC_QUARTERMASTER -> {
                list.add(new Offer(new ItemStack(Items.IRON_SWORD), 3, -1));
                list.add(new Offer(new ItemStack(Items.CROSSBOW), 4, -1));
                list.add(new Offer(new ItemStack(Items.ARROW, 16), 1, -1));
                list.add(new Offer(new ItemStack(Items.SHIELD), 2, -1));
                list.add(new Offer(new ItemStack(Items.IRON_CHESTPLATE), 6, -1));
                list.add(new Offer(new ItemStack(Items.IRON_HELMET), 4, -1));
                list.add(new Offer(new ItemStack(Items.DIAMOND_SWORD), 12, -1));
                list.add(new Offer(new ItemStack(Items.TNT, 4), 5, -1));
                list.add(new Offer(new ItemStack(ModItems.REPAIR_KIT, 2), 3, -1));
                list.add(new Offer(new ItemStack(Items.SPYGLASS), 3, -1));
            }
            case WONDERTAINMENT_REP -> {
                // Stock is limited and restocks every in-game day.
                list.add(new Offer(new ItemStack(Items.CAKE), 2, 3));
                list.add(new Offer(new ItemStack(Items.FIREWORK_ROCKET, 8), 2, 4));
                list.add(new Offer(new ItemStack(Items.COOKIE, 8), 1, 6));
                list.add(new Offer(new ItemStack(Items.MUSIC_DISC_CAT), 6, 1));
                list.add(new Offer(new ItemStack(Items.PINK_WOOL, 16), 1, 4));
                list.add(new Offer(new ItemStack(Items.TOTEM_OF_UNDYING), 30, 1));
                // A chance of other item-type SCPs, from the wondertainment_scps item tag.
                List<Item> scps = new ArrayList<>();
                BuiltInRegistries.ITEM.getTagOrEmpty(ModTags.WONDERTAINMENT_SCPS).forEach(h -> scps.add(h.value()));
                if (!scps.isEmpty()) {
                    Random r = new Random(Time.day(trader.level()) * 31 + trader.getId());
                    if (r.nextInt(3) == 0) list.add(new Offer(new ItemStack(scps.get(r.nextInt(scps.size()))), 25, 1));
                }
            }
            case MCD_CONTACT -> {
                for (ScpDefinition def : ScpApi.all()) {
                    if (def.mcdPrice <= 0) continue;
                    ItemStack flare = new ItemStack(ModItems.DELIVERY_FLARE);
                    flare.set(ModComponents.DELIVERY, def.id);
                    flare.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Delivery Flare: " + def.number));
                    list.add(new Offer(flare, def.mcdPrice, -1));
                }
            }
            default -> {}
        }
        return list;
    }

    public static void open(ServerPlayer player, TraderEntity trader) {
        List<Offer> offers = offers(trader);
        long day = Time.day(trader.level());
        if (trader.lastRestockDay != day || trader.stock.length != offers.size()) {
            trader.lastRestockDay = day;
            trader.stock = new int[offers.size()];
            for (int i = 0; i < offers.size(); i++) trader.stock[i] = offers.get(i).stock();
        }
        ChestGui gui = new ChestGui(trader.kind().displayName, 3);
        if (offers.isEmpty()) {
            gui.set(13, ChestGui.icon(Items.PAPER, "Nothing for sale", ChatFormatting.GRAY,
                    trader.kind() == TraderKind.MCD_CONTACT ? "\"Our inventory is... between shipments.\"" : "Come back later."));
        }
        for (int i = 0; i < Math.min(18, offers.size()); i++) {
            Offer o = offers.get(i);
            int index = i;
            List<String> lore = new ArrayList<>();
            lore.add("Price: $" + o.price());
            if (o.stock() >= 0) lore.add("In stock: " + trader.stock[i]);
            lore.add("You have $" + Cost.count(player, ModItems.DOLLAR) + ". Dollars are crafted from coins.");
            ItemStack icon = o.result().copy();
            icon.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                    lore.stream().map(s -> (Component) Component.literal(s).withStyle(ChatFormatting.GRAY)).toList()));
            gui.button(i, icon, (p, right) -> {
                if (o.stock() >= 0 && trader.stock[index] <= 0) {
                    p.sendSystemMessage(Component.literal("Sold out until tomorrow.").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!p.isCreative() && Cost.count(p, ModItems.DOLLAR) < o.price()) {
                    p.sendSystemMessage(Component.literal("You need $" + o.price() + ".").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!p.isCreative()) Cost.take(p, ModItems.DOLLAR, o.price());
                if (o.stock() >= 0) trader.stock[index]--;
                Cost.give(p, o.result().copy());
                ChestGui.later(() -> open(p, trader));
            });
        }
        gui.footer(null);
        gui.open(player);
    }

    private ShopGui() {}
}
