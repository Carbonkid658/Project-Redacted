package dev.projectredacted.gui.screen;

import dev.projectredacted.gui.*;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.gen.Box;
import dev.projectredacted.site.gen.PartFrames;
import dev.projectredacted.site.gen.PartKit;
import dev.projectredacted.site.gen.SitePart;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** The Part Frame's settings (design §7.1): name, kind, room type, weight, and the rooms inside the part. */
public class PartFrameScreen extends GuiScreen {
    private final PartFrames.Frame frame;
    private String category;
    private List<String> problems;

    public PartFrameScreen(PartFrames.Frame frame) {
        this.frame = frame;
        RoomType t = RoomTypes.get(frame.roomType);
        category = t == null ? null : t.category;
    }

    @Override
    public String id() {
        return "part_frame";
    }

    @Override
    public String invalid(ServerPlayer player) {
        if (!player.isCreative()) return "The Part Frame only works in creative mode.";
        return PartFrames.all().contains(frame) ? null : "That frame was deleted.";
    }

    @Override
    public View build(ServerPlayer p) {
        View v = new View("PART FRAME · " + frame.id).badge(frame.size());
        List<El> left = new ArrayList<>();
        left.add(El.input("frame_name", "Name, e.g. barracks_a", El.button("Rename", "rename")));
        left.add(El.text("Kind:", "gray"));
        left.add(El.chips("kind", frame.kind, "room", "Room", "hallway", "Hallway", "stairwell", "Stairwell", "entrance", "Entrance", "hub", "Hub", "gate", "Gate"));
        List<String> areas = new ArrayList<>(List.of("", "Shared"));
        for (var w : dev.projectredacted.site.gen.Areas.all()) {
            areas.add(w.id);
            areas.add(w.name);
        }
        left.add(El.row(El.text("Area:", "gray"), El.chips("area", frame.area == null ? "" : frame.area, areas.toArray(new String[0]))
                .tip("The area whose look this piece has, e.g. Living halls. Shared is for hubs, spine and tunnel pieces.")));
        List<El> tags = new ArrayList<>();
        tags.add(El.text("Tags:", "gray"));
        for (String t : SitePart.TAGS) tags.add(El.checkbox(t, frame.tags.contains(t), "tag_" + t).tip(switch (t) {
            case SitePart.TAG_MAIN_HALL -> "Hallway: a piece of an area's main hall.";
            case SitePart.TAG_SIDE_HALL -> "Hallway: a piece of a side hall.";
            case SitePart.TAG_SPINE -> "Hallway: a segment of the central spine.";
            default -> "Hallway: a long tunnel between sectors.";
        }));
        left.add(El.row(tags));
        left.add(El.stepper("Weight (how often this variant is picked)", frame.weight, 1, 100, "weight"));
        RoomType type = RoomTypes.get(frame.roomType);
        left.add(El.text("Room type: " + (type == null ? "none" : type.name), "white"));
        left.add(El.text("Rooms registered inside: " + frame.rooms.size(), "gray"));
        for (int i = 0; i < frame.rooms.size(); i++) {
            PartFrames.FrameRoom r = frame.rooms.get(i);
            RoomType rt = RoomTypes.get(r.type);
            left.add(El.row(El.text((rt == null ? r.type : rt.name) + " · door " + r.door), El.button("Remove", "remove_room").arg("index", i)));
        }
        left.add(El.text("For several rooms in one part, put each room's key block inside it; each closed space becomes its own room.", "gray"));
        left.add(El.row(El.button("Check", "check").style("primary").tip("Reads the blocks now and lists anything that breaks the building standard."),
                El.button("Delete frame", "delete").style("danger").confirm("Delete frame " + frame.id + "? The blocks stay.")));
        if (problems != null) {
            if (problems.isEmpty()) left.add(El.text("✔ Follows the building standard.", "green"));
            for (String s : problems) left.add(El.text("✖ " + s, "red"));
        }

        El.ListEl cats = El.list("categories", "category").fill();
        cats.add("", "No room type").selected(frame.roomType == null && category == null);
        for (String c : RoomTypes.CATEGORIES) {
            List<RoomType> types = RoomTypes.inCategory(c);
            if (types.isEmpty()) continue;
            cats.add(c, c).icon(types.get(0).iconItem()).color(Ui.categoryColor(c)).selected(c.equals(category));
        }
        El.ListEl types = El.list("types", "room_type").fill();
        if (category != null) {
            for (RoomType t : RoomTypes.inCategory(category)) types.add(t.id, t.name).icon(t.iconItem()).selected(t.id.equals(frame.roomType));
        }
        v.add(El.cols(El.col(0.5, left), El.col(0.22, List.of(cats)), El.col(0.28, List.of(types))));
        v.footer("Export every frame with /site parts export <kit name>");
        return v;
    }

    @Override
    public void handle(ServerPlayer p, String action, Args a) {
        switch (action) {
            case "rename" -> {
                String id = PartFrames.cleanId(a.text());
                if (id == null) return;
                if (PartFrames.byId(id) != null && PartFrames.byId(id) != frame) {
                    Gui.toast(p, "Another frame is already called " + id + ".", true);
                    return;
                }
                frame.id = id;
            }
            case "kind" -> frame.kind = SitePart.Kind.parse(a.str("id", "room")).name().toLowerCase();
            case "area" -> {
                String w = a.str("id", "");
                frame.area = w.isEmpty() ? null : w;
            }
            case "tag_main_hall", "tag_side_hall", "tag_spine", "tag_tunnel" -> {
                String t = action.substring(4);
                if (!SitePart.TAGS.contains(t)) return;
                if (!frame.tags.remove(t)) frame.tags.add(t);
            }
            case "weight" -> frame.weight = Math.max(1, Math.min(100, a.num("value", frame.weight)));
            case "category" -> {
                category = a.str("id", "");
                if (category.isEmpty()) {
                    category = null;
                    frame.roomType = null;
                }
            }
            case "room_type" -> frame.roomType = RoomTypes.get(a.str("id")) == null ? null : a.str("id");
            case "remove_room" -> {
                int i = a.num("index", -1);
                if (i >= 0 && i < frame.rooms.size()) frame.rooms.remove(i);
            }
            case "check" -> problems = check(p);
            case "delete" -> {
                PartFrames.remove(frame);
                Gui.close(p);
                return;
            }
            default -> {}
        }
        PartFrames.save();
    }

    private List<String> check(ServerPlayer p) {
        try {
            SitePart part = dev.projectredacted.command.PartsCommand.read(p.level(), frame);
            return PartKit.check(part);
        } catch (RuntimeException e) {
            return List.of("Couldn't read the blocks: " + e.getMessage());
        }
    }
}
