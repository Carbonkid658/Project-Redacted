package dev.projectredacted.gui;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The Circuit Breaker: add rooms to (or remove them from) the site's power. */
public final class PowerGui {
    public static void open(ServerPlayer player, Site site, int page) {
        List<Room> rooms = new ArrayList<>();
        for (Room r : site.rooms) {
            RoomType t = r.typeDef();
            if (t != null && t.powerDraw > 0) rooms.add(r);
        }
        int connected = 0;
        for (Room r : rooms) if (r.connected) connected++;
        ChestGui gui = new ChestGui("Circuit Breaker", 6);
        int perPage = 45;
        int pages = Math.max(1, (rooms.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < perPage && page * perPage + i < rooms.size(); i++) {
            Room r = rooms.get(page * perPage + i);
            RoomType t = r.typeDef();
            String state = !r.built ? "Under construction" : r.integrity <= 0 ? "Destroyed: needs repair"
                    : r.poweredNow ? "POWERED" : r.connected ? "Connected, not enough power or slots" : "Disconnected";
            ChatFormatting color = r.poweredNow ? ChatFormatting.YELLOW : r.connected ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            gui.button(i, ChestGui.icon(r.poweredNow ? Items.REDSTONE_TORCH : r.connected ? Items.REDSTONE : Items.GUNPOWDER,
                    (r.poweredNow ? "⚡ " : "") + r.displayName(), color,
                    "Draws " + t.powerDraw + " power" + (t.waterDraw > 0 ? ", " + t.waterDraw + " water" : ""),
                    state, "", "Click to " + (r.connected ? "disconnect" : "connect")), (p, right) -> {
                if (!r.connected && countConnected(site) >= site.connectCapacity) {
                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "No free breaker slots. Build another Circuit Breaker room.").withStyle(ChatFormatting.RED));
                } else {
                    r.connected = !r.connected;
                }
                ChestGui.later(() -> open(p, site, 0));
            });
        }
        gui.set(49, ChestGui.icon(Items.LIGHTNING_ROD.asList().get(0), "Power: " + site.powerDemand + " / " + site.powerSupply, ChatFormatting.YELLOW,
                "Generators supply " + site.powerSupply + " power.",
                "Breaker slots used: " + connected + " / " + site.connectCapacity,
                "Water: " + site.waterDemand + " / " + site.waterSupply,
                site.mainframeOnline() ? "Mainframe online" : "MAINFRAME OFFLINE: nothing is powered"));
        final int current = page;
        if (page > 0) gui.button(45, ChestGui.icon(Items.ARROW, "Previous page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> open(p, site, current - 1)));
        if (page < pages - 1) gui.button(53, ChestGui.icon(Items.ARROW, "Next page", ChatFormatting.GRAY), (p, r) -> ChestGui.later(() -> open(p, site, current + 1)));
        gui.footer(null);
        gui.open(player);
    }

    private static int countConnected(Site site) {
        int n = 0;
        for (Room r : site.rooms) if (r.connected) n++;
        return n;
    }

    private PowerGui() {}
}
