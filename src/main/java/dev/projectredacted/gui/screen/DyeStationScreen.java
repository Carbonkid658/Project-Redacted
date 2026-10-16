package dev.projectredacted.gui.screen;

import dev.projectredacted.blockentities.DyeStationBlockEntity;
import dev.projectredacted.blockentities.DyedBlockEntity;
import dev.projectredacted.gui.*;
import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Dye Station: put a stack of dyeable items in, pick a color on the wheel or type a hex code, and Dye applies
 * it to the whole stack the way dye applies to leather.
 */
public class DyeStationScreen extends GuiScreen {
    /** The vanilla dye colors, with pure white first because white means undyed. */
    static final int[] SWATCHES = {
            0xFFFFFF, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    private final BlockPos at;

    public DyeStationScreen(BlockPos at) {
        this.at = at;
    }

    @Override
    public String id() {
        return "dye_station";
    }

    private DyeStationBlockEntity station(ServerPlayer p) {
        return p.level().getBlockEntity(at) instanceof DyeStationBlockEntity s ? s : null;
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (!Ui.near(player, at) || station(player) == null) return "You stepped away from the Dye Station.";
        return null;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** "#1a2B3c", "1A2B3C", "0x1A2B3C" or the short "#abc"; -1 if it isn't a color. */
    public static int parseHex(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x")) s = s.substring(2);
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        if (!s.matches("[0-9a-f]{6}")) return -1;
        return Integer.parseInt(s, 16);
    }

    @Override
    public View build(ServerPlayer p) {
        DyeStationBlockEntity st = station(p);
        View v = new View("DYE STATION");
        ItemStack held = st.held();
        int color = st.color();

        List<El> left = new ArrayList<>();
        if (held.isEmpty()) {
            left.add(El.box("In the station", El.text("Empty. Pick a stack below, or right-click the station holding one.", "gray")));
        } else {
            El.Items in = El.items("take");
            in.add("held", held, "");
            left.add(El.box("In the station", in,
                    El.text(Ui.name(held.getItem()) + " · " + (DyeStationBlockEntity.colorOf(held) == DyedBlockEntity.UNDYED ? "undyed" : hex(DyeStationBlockEntity.colorOf(held))), "gray"),
                    El.button("Take out", "take").width(70)));
        }
        El.Items mine = El.items("insert");
        Inventory inv = p.getInventory();
        int found = 0;
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack s = inv.getItem(i);
            if (!DyeStationBlockEntity.dyeable(s)) continue;
            mine.add(String.valueOf(i), s, "").add("tip", tipOf(s));
            found++;
        }
        left.add(El.box("Your dyeable items", found == 0 ? El.text("None. Dyeable Bricks, Floor and Tile, and leather, can be dyed.", "gray") : mine));

        List<El> right = new ArrayList<>();
        right.add(El.colorWheel("wheel", color, "pick", held.isEmpty() ? ModBlocks.DYEABLE_BRICKS : held.getItem(), SWATCHES));
        right.add(El.input("hex", "Hex code, like #3AB3DA", El.button("Set", "hex")));
        String why = held.isEmpty() ? "Put something in first." : DyeStationBlockEntity.colorOf(held) == color ? "It's already this color." : null;
        String label = color == DyedBlockEntity.UNDYED ? "Wash off the dye" : "Dye " + (held.isEmpty() ? "" : held.getCount() + " ") + hex(color);
        right.add(El.button(label, "dye").style("primary").disabled(why));

        v.add(El.cols(El.col(0.42, left), El.col(0.58, right)));
        v.footer("White washes the dye off.");
        return v;
    }

    private static com.google.gson.JsonArray tipOf(ItemStack s) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        a.add(s.getCount() + "× " + Ui.name(s.getItem()));
        int c = DyeStationBlockEntity.colorOf(s);
        a.add(c == DyedBlockEntity.UNDYED ? "Undyed" : "Dyed " + hex(c));
        a.add("Click to put it in the station.");
        return a;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        DyeStationBlockEntity st = station(p);
        switch (action) {
            case "pick" -> st.setColor(a.num("color", st.color()));
            case "hex" -> {
                int rgb = parseHex(a.text());
                if (rgb < 0) Gui.toast(p, "\"" + a.text() + "\" isn't a hex color. Use six digits, like #3AB3DA.", true);
                else st.setColor(rgb);
            }
            case "insert" -> {
                int slot = parseInt(a.str("id"));
                Inventory inv = p.getInventory();
                if (slot < 0 || slot >= inv.getNonEquipmentItems().size()) return;
                ItemStack s = inv.getItem(slot);
                if (!DyeStationBlockEntity.dyeable(s)) return;
                inv.setItem(slot, ItemStack.EMPTY);
                ItemStack rest = st.insert(p, s);
                if (!rest.isEmpty() && !inv.add(rest)) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), rest));
            }
            case "take" -> st.takeOut(p);
            case "dye" -> {
                int n = st.dye();
                if (n > 0) Gui.toast(p, st.color() == DyedBlockEntity.UNDYED ? "Washed the dye off " + n + "." : "Dyed " + n + " " + hex(st.color()) + ".", false);
            }
            default -> {}
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
