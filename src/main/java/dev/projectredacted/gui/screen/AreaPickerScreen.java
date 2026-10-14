package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.Areas;
import dev.projectredacted.site.gen.HandHalls;
import dev.projectredacted.site.gen.PlacedPart;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Picks the area a hand-built hallway belongs to (Area Hall Marker, v4 §5.8, V25). */
public class AreaPickerScreen extends GuiScreen {
    private final Site site;
    private final BlockPos marker;

    public AreaPickerScreen(Site site, BlockPos marker) {
        this.site = site;
        this.marker = marker;
    }

    @Override
    public String id() {
        return "area_picker";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (SiteManager.byId(site.id) != site || !site.canBuild(player)) return "You need build rights in this site.";
        if (!player.level().getBlockState(marker).is(ModBlocks.AREA_HALL_MARKER)) return "The marker is gone.";
        return null;
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("AREA HALL MARKER").size("medium").badge(Ui.clr(site, p));
        String current = null;
        for (PlacedPart part : site.parts) if (!part.removed && marker.equals(part.marker)) current = part.area;
        v.add(El.text(current == null ? "Which area is this hallway part of? Rooms of that area can then be added along it."
                : "This hallway is part of the " + Areas.displayName(site, current) + " area.", "gray"));
        List<El> buttons = new ArrayList<>();
        for (Areas.Area a : Areas.all()) {
            boolean picked = current != null && Areas.kindOf(current).equals(a.id);
            El.Button b = El.button(a.name, "pick").arg("id", a.id);
            buttons.add(picked ? b.style("primary") : b);
        }
        v.add(El.row(buttons));
        v.add(El.text("The hallway needs walls, a floor and a roof. It ends where it meets the facility's own halls and rooms.", "gray"));
        if (current != null) v.add(El.button("Not an area hall", "clear"));
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "pick" -> {
                Areas.Area area = Areas.get(a.str("id"));
                if (area == null) return;
                String problem = HandHalls.register((ServerLevel) p.level(), site, marker, area.id);
                if (problem != null) {
                    Gui.toast(p, problem, true);
                    return;
                }
                Gui.toast(p, "This hallway is now part of the " + area.name + " area.", false);
                Gui.close(p);
            }
            case "clear" -> {
                HandHalls.remove(site, marker);
                Gui.toast(p, "The hallway is no longer an area hall.", false);
            }
            default -> {}
        }
    }
}
