package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.npc.Shops;
import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.npc.TraderKind;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Time;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** GOC Quartermaster, Wondertainment Rep and MC&D Contact (GUI design §9.1), priced in Dollars. */
public class ShopScreen extends GuiScreen {
    private static final String DOLLARS_HELP = "Dollars are crafted from Coins at a crafting table. Administration staff earn Coins for your site: "
            + "collect them at the Mainframe Terminal.";

    private final TraderEntity trader;
    private int selected = 0;
    private int qty = 1;

    public ShopScreen(TraderEntity trader) {
        this.trader = trader;
    }

    @Override
    public String id() {
        return "shop";
    }

    @Override
    public boolean live() {
        return trader.kind() == TraderKind.WONDERTAINMENT_REP;
    }

    @Override
    public String invalid(ServerPlayer player) {
        return Ui.near(player, trader) ? null : "The trader is out of reach.";
    }

    private static String flavor(TraderKind kind) {
        return switch (kind) {
            case GOC_QUARTERMASTER -> "Weapons. Tech. No questions.";
            case WONDERTAINMENT_REP -> "Fun for the whole family!";
            case MCD_CONTACT -> "Discretion is included in the price.";
            default -> "";
        };
    }

    private int wallet(ServerPlayer p) {
        return Cost.count(p, ModItems.DOLLAR);
    }

    @Override
    public View build(ServerPlayer p) {
        List<Shops.Offer> offers = Shops.restock(trader);
        TraderKind kind = trader.kind();
        View v = new View(kind.displayName.toUpperCase() + " · \"" + flavor(kind) + "\"").face(Ui.traderSkin(kind));
        List<El> top = new ArrayList<>();
        top.add(El.text("Wallet: $" + wallet(p) + (p.isCreative() ? " (creative: free)" : ""), "gold"));
        if (kind == TraderKind.WONDERTAINMENT_REP) top.add(El.text("Restocks in " + Ui.duration(24000 - Time.dayTime(trader.level())) + " (in-game day)", "gray"));
        top.add(El.info("How do I get Dollars?", DOLLARS_HELP).width(110));
        v.add(El.row(top));
        if (offers.isEmpty()) {
            v.add(El.center(kind == TraderKind.MCD_CONTACT ? "\"Our inventory is... between shipments.\"" : "Nothing for sale. Come back later.", "gray"));
            return v;
        }
        selected = Math.max(0, Math.min(selected, offers.size() - 1));
        El.Items grid = El.items("select");
        for (int i = 0; i < offers.size(); i++) {
            Shops.Offer o = offers.get(i);
            var e = grid.add(String.valueOf(i), o.result().getItem(), o.result().getCount(), "$" + o.price());
            if (i == selected) e.addProperty("selected", true);
            if (o.stock() >= 0 && trader.stock[i] <= 0) e.addProperty("dim", true);
            com.google.gson.JsonArray tip = new com.google.gson.JsonArray();
            tip.add(o.result().getHoverName().getString());
            tip.add("$" + o.price() + (o.stock() >= 0 ? " · " + trader.stock[i] + " left" : ""));
            e.add("tip", tip);
        }
        v.add(El.cols(El.col(0.5, List.of(grid)), El.col(0.5, detail(p, offers.get(selected)))));
        return v;
    }

    private List<El> detail(ServerPlayer p, Shops.Offer o) {
        List<El> out = new ArrayList<>();
        ItemStack stack = o.result();
        out.add(El.heading(stack.getHoverName().getString().toUpperCase()));
        if (o.scp() != null) {
            out.add(El.badges(o.scp().number, "white", o.scp().objectClass == null ? null : o.scp().objectClass.name(), objectClassColor(o.scp().objectClass)));
            out.add(El.text("Delivered by flare. Use it where you want the delivery.", "gray"));
        }
        out.add(El.text("Price: $" + o.price() + (stack.getCount() > 1 ? " for " + stack.getCount() : "")));
        int left = o.stock() >= 0 ? trader.stock[selected] : -1;
        out.add(El.text("Stock: " + (left < 0 ? "unlimited" : left + " left today"), left == 0 ? "red" : "gray"));
        int max = left < 0 ? 64 : Math.max(1, left);
        qty = Math.max(1, Math.min(qty, max));
        out.add(El.stepper("Qty", qty, 1, max, "qty"));
        out.add(El.text("Total $" + o.price() * qty, "gold"));
        out.add(El.button("Buy", "buy").style("confirm").disabled(buyBlocker(p, o, qty)).tip("Shift-click buys five times as many."));
        return out;
    }

    private static String objectClassColor(dev.projectredacted.api.ObjectClass c) {
        if (c == null) return "gray";
        return switch (c) {
            case SAFE -> "green";
            case EUCLID -> "amber";
            case KETER -> "red";
            case THAUMIEL -> "purple";
            default -> "gray";
        };
    }

    private String buyBlocker(ServerPlayer p, Shops.Offer o, int count) {
        if (o.stock() >= 0 && trader.stock[selected] < count) return trader.stock[selected] <= 0 ? "Sold out until tomorrow." : "Only " + trader.stock[selected] + " left.";
        if (!p.isCreative() && wallet(p) < o.price() * count) return "You need $" + o.price() * count + " (you have $" + wallet(p) + ").";
        return null;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "select" -> {
                selected = TerminalScreen.parseInt(a.str("id"), 0);
                qty = 1;
            }
            case "qty" -> qty = a.num("value", qty);
            case "buy" -> {
                List<Shops.Offer> offers = Shops.restock(trader);
                if (selected < 0 || selected >= offers.size()) return;
                Shops.Offer o = offers.get(selected);
                int count = a.shift() ? qty * 5 : qty;
                if (o.stock() >= 0) count = Math.min(count, trader.stock[selected]);
                if (!p.isCreative()) count = Math.min(count, wallet(p) / Math.max(1, o.price()));
                String blocker = buyBlocker(p, o, Math.max(1, count));
                if (count <= 0 || blocker != null) {
                    Gui.toast(p, blocker != null ? blocker : "You can't afford that.", true);
                    return;
                }
                if (!p.isCreative()) Cost.take(p, ModItems.DOLLAR, o.price() * count);
                if (o.stock() >= 0) trader.stock[selected] -= count;
                for (int i = 0; i < count; i++) Cost.give(p, o.result().copy());
                Gui.toast(p, "Purchased " + (count > 1 ? count + "× " : "") + o.result().getHoverName().getString() + ".", false);
            }
            default -> {}
        }
    }
}
