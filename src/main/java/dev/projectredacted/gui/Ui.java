package dev.projectredacted.gui;

import dev.projectredacted.npc.TraderKind;
import dev.projectredacted.site.Site;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the screens. */
public final class Ui {
    public static final double REACH = 8;

    public static String clr(Site site, Player player) {
        return site == null ? null : "CLR " + site.clearance(player);
    }

    /** A staff member's skin texture (NPC revamp §4.1); the pool's first skin when none was picked yet. */
    public static String staffSkin(StaffRole role, String skin) {
        return dev.projectredacted.npc.Skins.texture(dev.projectredacted.npc.Skins.STAFF, role.skin(), skin);
    }

    public static String traderSkin(TraderKind kind) {
        return dev.projectredacted.npc.Skins.texture(dev.projectredacted.npc.Skins.TRADER, kind.skin, "");
    }

    /** Have/need per item, for the cost widget. Creative players count as having everything. */
    public static List<El.CostLine> costLines(Player player, Cost cost) {
        List<El.CostLine> lines = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : cost.items.entrySet()) {
            int have = player.isCreative() ? e.getValue() : Cost.count(player, e.getKey());
            lines.add(new El.CostLine(e.getKey(), name(e.getKey()), have, e.getValue()));
        }
        return lines;
    }

    /** The first item the player is short of, as "Needs 4 more Iron Ingot", or null. */
    public static String shortfall(Player player, Cost cost) {
        if (player.isCreative()) return null;
        for (Map.Entry<Item, Integer> e : cost.items.entrySet()) {
            int have = Cost.count(player, e.getKey());
            if (have < e.getValue()) return "Needs " + (e.getValue() - have) + " more " + name(e.getKey());
        }
        return null;
    }

    public static int have(Player player, Item item) {
        return Cost.count(player, item);
    }

    public static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    /** "4m 00s" from ticks. */
    public static String duration(long ticks) {
        long s = Math.max(0, ticks) / 20;
        if (s >= 3600) return String.format("%dh %02dm", s / 3600, s / 60 % 60);
        return String.format("%dm %02ds", s / 60, s % 60);
    }

    public static boolean near(Player player, BlockPos pos) {
        return pos != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH;
    }

    public static boolean near(Player player, Entity entity) {
        return entity != null && entity.isAlive() && entity.level() == player.level() && player.distanceToSqr(entity) <= REACH * REACH;
    }

    /** Color name for a room category, used on the facility map and in legends. */
    public static String categoryColor(String category) {
        return switch (category) {
            case "Core" -> "gold";
            case "Offices" -> "blue";
            case "Engineering" -> "amber";
            case "Medical" -> "pink";
            case "Common" -> "green";
            case "Research" -> "aqua";
            case "Containment" -> "purple";
            case "Checkpoint", "Security" -> "red";
            case "Housing" -> "teal";
            default -> "gray";
        };
    }

    public static String yes(boolean b) {
        return b ? "yes" : "no";
    }

    private Ui() {}
}
