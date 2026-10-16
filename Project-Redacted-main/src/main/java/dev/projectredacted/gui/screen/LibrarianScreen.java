package dev.projectredacted.gui.screen;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.gui.*;
import dev.projectredacted.npc.Library;
import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.npc.TraderKind;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.registry.ModTags;
import dev.projectredacted.util.Cost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The Librarian in the Wanderers' Library (GUI design §9.2): hand over SCP items you carry, or SCP entities
 * within 8 blocks, for Library Tomes and Dollars. Dangerous SCPs pay more.
 */
public class LibrarianScreen extends GuiScreen {
    private final TraderEntity librarian;
    private String selected = "";

    public LibrarianScreen(TraderEntity librarian) {
        this.librarian = librarian;
    }

    private record Anomaly(String id, String name, String where, int weight, ScpDefinition def, int slot, Entity entity) {}

    @Override
    public String id() {
        return "librarian";
    }

    @Override
    public String invalid(ServerPlayer player) {
        return Ui.near(player, librarian) ? null : "The Librarian is out of reach.";
    }

    private List<Anomaly> anomalies(ServerPlayer p) {
        List<Anomaly> list = new ArrayList<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ModTags.SCP_ITEMS)) continue;
            ScpDefinition def = ScpApi.forItem(stack.getItem());
            list.add(new Anomaly("slot:" + i, def != null ? def.number : stack.getHoverName().getString(), "item, in your inventory",
                    Math.max(1, ScpApi.raidWeight(def)), def, i, null));
        }
        for (Entity e : p.level().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(8), x -> x.getType().builtInRegistryHolder().is(ModTags.SCP_ENTITIES))) {
            ScpDefinition def = ScpApi.forEntity(e.getType());
            list.add(new Anomaly("entity:" + e.getUUID(), def != null ? def.number : e.getName().getString(), "following you",
                    Math.max(1, ScpApi.raidWeight(def)) * 2, def, -1, e));
        }
        return list;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("THE LIBRARIAN · \"Bring us anomalies.\"").face(Ui.traderSkin(TraderKind.LIBRARIAN));
        List<Anomaly> list = anomalies(p);
        El.ListEl rows = El.list("anomalies", "select").empty("You carry no anomalies, and none are with you.").fill();
        Anomaly chosen = null;
        for (Anomaly a : list) {
            if (a.id.equals(selected)) chosen = a;
            rows.add(a.id, a.name + " (" + a.where + ")").icon(a.entity != null ? Items.LEAD : p.getInventory().getItem(a.slot).getItem())
                    .sub(a.weight + " Tomes + $" + 5 * a.weight, "purple").selected(a.id.equals(selected));
        }
        v.add(El.text("YOUR ANOMALIES · REWARD", "gray"));
        v.add(rows);
        if (chosen != null) {
            String cls = chosen.def != null && chosen.def.objectClass != null ? " " + chosen.def.objectClass.name().charAt(0) + chosen.def.objectClass.name().substring(1).toLowerCase() + "." : "";
            v.add(El.text("Selected: " + chosen.name + "." + cls + " Higher danger pays more.", "white"));
            El.Button give = El.button("Hand over", "give").style("primary");
            if (chosen.entity != null) give.confirm("Hand over " + chosen.name + "? It's gone for good.");
            else give.tip("Shift-click hands over five.");
            v.add(give);
        }
        v.add(El.text("Dealing with the Hand never stops their raids.", "gray"));
        v.add(El.button("Return to the Overworld", "leave"));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "select" -> selected = a.str("id");
            case "leave" -> {
                Gui.close(p);
                Library.leave(p);
            }
            case "give" -> {
                for (Anomaly an : anomalies(p)) {
                    if (!an.id.equals(selected)) continue;
                    if (an.entity != null) {
                        if (!an.entity.isAlive()) return;
                        an.entity.discard();
                        reward(p, an.weight);
                    } else {
                        ItemStack s = p.getInventory().getItem(an.slot);
                        int n = Math.min(a.shift() ? 5 : 1, s.getCount());
                        if (!s.is(ModTags.SCP_ITEMS) || n <= 0) return;
                        s.shrink(n);
                        reward(p, an.weight * n);
                    }
                    selected = "";
                    Gui.toast(p, "\"The Library thanks you.\"", false);
                    return;
                }
            }
            default -> {}
        }
    }

    private static void reward(ServerPlayer player, int weight) {
        Cost.give(player, new ItemStack(ModItems.LIBRARY_TOME, weight));
        Cost.give(player, new ItemStack(ModItems.DOLLAR, 5 * weight));
    }
}
