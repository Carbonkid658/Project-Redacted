package dev.projectredacted.gui;

import dev.projectredacted.room.*;
import dev.projectredacted.site.Site;
import dev.projectredacted.util.Cost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Room Configuration Device menus: pick a room, preview it as ghost blocks, rotate, reroll, then build. */
public final class DesignatorGui {
    public static void openCategories(ServerPlayer player, Site site, DesignSession s) {
        ChestGui gui = new ChestGui("Room Configuration: " + s.describeSize(), 3);
        int slot = 0;
        for (String cat : RoomTypes.CATEGORIES) {
            List<RoomType> types = RoomTypes.inCategory(cat);
            if (types.isEmpty()) continue;
            gui.button(slot++, ChestGui.icon(types.get(0).iconItem(), cat, ChatFormatting.AQUA, types.size() + " room type(s)"),
                    (p, r) -> ChestGui.later(() -> openTypes(p, site, s, cat)));
        }
        gui.button(22, ChestGui.icon(Items.BARRIER, "Clear corners", ChatFormatting.RED), (p, r) -> {
            s.reset();
            p.closeContainer();
        });
        gui.footer(null);
        gui.open(player);
    }

    public static void openTypes(ServerPlayer player, Site site, DesignSession s, String category) {
        ChestGui gui = new ChestGui("Rooms: " + category, 3);
        int slot = 0;
        for (RoomType t : RoomTypes.inCategory(category)) {
            String locked = RoomPlanner.lockedReason(site, t);
            List<String> lore = new ArrayList<>(List.of(ConfirmGui.wrap(t.description)));
            lore.add("Size: " + t.minSide + "-" + t.maxSide + " wide, " + t.minHeight + "-" + t.maxHeight + " tall");
            lore.add("Power " + t.powerDraw + (t.waterDraw > 0 ? ", water " + t.waterDraw : ""));
            lore.add("Base cost: " + String.join(", ", t.cost().describe()));
            if (locked != null) lore.add("LOCKED: " + locked);
            gui.button(slot++, ChestGui.icon(locked == null ? t.iconItem() : Items.DYE.gray(), t.name, locked == null ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY, lore),
                    (p, r) -> {
                        if (locked != null) {
                            p.sendSystemMessage(Component.literal(t.name + ": " + locked).withStyle(ChatFormatting.RED));
                            return;
                        }
                        s.typeId = t.id;
                        s.previewing = true;
                        ChestGui.later(() -> openPreview(p, site, s));
                    });
            if (slot >= 18) break;
        }
        gui.footer((p, r) -> ChestGui.later(() -> openCategories(p, site, s)));
        gui.open(player);
    }

    public static void openPreview(ServerPlayer player, Site site, DesignSession s) {
        RoomType type = RoomTypes.get(s.typeId);
        if (type == null) {
            s.typeId = null;
            openCategories(player, site, s);
            return;
        }
        String problem = RoomPlanner.validate(site, s, type);
        Cost cost = RoomPlanner.cost(type, s);
        ChestGui gui = new ChestGui(type.name + " " + s.describeSize(), 3);
        gui.set(4, ChestGui.icon(type.iconItem(), type.name, ChatFormatting.GOLD,
                problem == null ? "Ready to build." : "Problem: " + problem,
                "Cost: " + String.join(", ", cost.describe()),
                "Door wall: " + new String[]{"north", "east", "south", "west"}[s.rotation & 3]));
        gui.button(10, ChestGui.icon(s.previewing ? Items.ENDER_EYE : Items.ENDER_PEARL, s.previewing ? "Hide ghost preview" : "Show ghost preview", ChatFormatting.AQUA,
                "Ghost particles show where blocks will go", "while you hold the device."), (p, r) -> {
            s.previewing = !s.previewing;
            ChestGui.later(() -> openPreview(p, site, s));
        });
        gui.button(11, ChestGui.icon(Items.COMPASS, "Rotate", ChatFormatting.AQUA, "Moves the door to the next wall."), (p, r) -> {
            s.rotation = (s.rotation + 1) & 3;
            ChestGui.later(() -> openPreview(p, site, s));
        });
        gui.button(12, ChestGui.icon(Items.CLOCK, "Reroll", ChatFormatting.AQUA, "New palette and furniture layout."), (p, r) -> {
            s.reroll();
            ChestGui.later(() -> openPreview(p, site, s));
        });
        gui.button(14, ChestGui.icon(problem == null ? Items.CONCRETE.lime() : Items.CONCRETE.red(), "Build", problem == null ? ChatFormatting.GREEN : ChatFormatting.RED,
                "Pays the cost and starts construction.", "Rooms stay dormant until added to the Circuit Breaker."), (p, r) -> {
            p.closeContainer();
            RoomPlanner.commit(site, p, s, false);
        });
        gui.button(15, ChestGui.icon(Items.OAK_SIGN, "Register as hand-built", ChatFormatting.YELLOW,
                "You built this room yourself (manual override).", "Registers it as this room type for free."), (p, r) -> {
            p.closeContainer();
            RoomPlanner.commit(site, p, s, true);
        });
        gui.button(16, ChestGui.icon(Items.BOOK, "Change room type", ChatFormatting.GRAY), (p, r) -> {
            s.typeId = null;
            s.previewing = false;
            ChestGui.later(() -> openCategories(p, site, s));
        });
        gui.button(22, ChestGui.icon(Items.BARRIER, "Cancel design", ChatFormatting.RED), (p, r) -> {
            s.reset();
            p.closeContainer();
        });
        gui.footer(null);
        gui.open(player);
    }

    private DesignatorGui() {}
}
