package dev.projectredacted.site.gen;

import com.google.gson.*;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * A set of parts (design §3.3). The default kit is every part in data/&lt;ns&gt;/projectredacted/site_parts/
 * (a .nbt and a .json per part). Anything the kit lacks comes from the placeholder kit, so a site always
 * generates. Kits exported from a creative world can also be loaded from their folder for testing.
 */
public class PartKit {
    private static final Gson GSON = new Gson();
    private static PartKit active = new PartKit("default");
    private static final Map<String, SitePart> PLACEHOLDERS = new LinkedHashMap<>();
    private static final Map<String, PartKit> WORLD_KITS = new HashMap<>();

    public final String name;
    public final List<SitePart> parts = new ArrayList<>();
    /** Problems found while loading, for the check report. */
    public final List<String> problems = new ArrayList<>();

    public PartKit(String name) {
        this.name = name;
    }

    public static PartKit active() {
        return active;
    }

    // ---------------------------------------------------------------- lookups

    public List<SitePart> ofKind(SitePart.Kind kind) {
        List<SitePart> out = new ArrayList<>();
        for (SitePart p : parts) if (p.kind == kind) out.add(p);
        if (out.isEmpty()) for (SitePart p : placeholders()) if (p.kind == kind) out.add(p);
        return out;
    }

    /** Parts that contain this room type; the placeholder sizes if the kit has none (§2.8). */
    public List<SitePart> forRoom(String roomType) {
        List<SitePart> out = new ArrayList<>();
        for (SitePart p : parts) if (p.kind != SitePart.Kind.ENTRANCE && p.roomTypes().contains(roomType)) out.add(p);
        if (!out.isEmpty()) return out;
        for (SitePart p : placeholders()) if (p.kind == SitePart.Kind.ROOM && roomType.equals(p.roomType)) out.add(p);
        if (!out.isEmpty()) return out;
        RoomType t = RoomTypes.get(roomType);
        if (t == null) return out;
        for (SitePart p : ProceduralParts.roomSizes(t)) out.add(PLACEHOLDERS.computeIfAbsent(p.id, k -> p));
        return out;
    }

    public boolean hasOwnPart(String roomType) {
        for (SitePart p : parts) if (p.roomTypes().contains(roomType)) return true;
        return false;
    }

    public SitePart byId(String kit, String id) {
        if ("placeholder".equals(kit)) return placeholder(id);
        PartKit k = name.equals(kit) ? this : WORLD_KITS.getOrDefault(kit, this);
        for (SitePart p : k.parts) if (p.id.equals(id)) return p;
        return placeholder(id);
    }

    public static SitePart placeholder(String id) {
        placeholders();
        SitePart p = PLACEHOLDERS.get(id);
        if (p != null) return p;
        for (RoomType t : RoomTypes.all()) {
            for (SitePart s : ProceduralParts.roomSizes(t)) {
                PLACEHOLDERS.putIfAbsent(s.id, s);
                if (s.id.equals(id)) return PLACEHOLDERS.get(id);
            }
        }
        return null;
    }

    private static Collection<SitePart> placeholders() {
        if (PLACEHOLDERS.isEmpty()) for (SitePart p : ProceduralParts.level0Kit()) PLACEHOLDERS.put(p.id, p);
        List<SitePart> base = new ArrayList<>();
        for (SitePart p : PLACEHOLDERS.values()) if (p.kind != SitePart.Kind.ROOM || isLevel0Room(p)) base.add(p);
        return base;
    }

    private static boolean isLevel0Room(SitePart p) {
        return RoomTypes.DIRECTORS_OFFICE.equals(p.roomType) || RoomTypes.BARRACKS.equals(p.roomType);
    }

    /** Sizes on offer for a room type, smallest first, as "11×9" labels. */
    public List<SitePart> sizes(String roomType) {
        List<SitePart> list = new ArrayList<>(forRoom(roomType));
        list.sort(Comparator.comparingInt(p -> p.sx * p.sz));
        return list;
    }

    // ---------------------------------------------------------------- loading

    /** Reloads the default kit from datapacks. */
    public static void reload(ResourceManager manager) {
        PartKit kit = new PartKit("default");
        Map<Identifier, Resource> found = manager.listResources("projectredacted/site_parts", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            Identifier jsonId = e.getKey();
            String path = jsonId.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            Identifier nbtId = Identifier.fromNamespaceAndPath(jsonId.getNamespace(), path.substring(0, path.length() - 5) + ".nbt");
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                Optional<Resource> nbt = manager.getResource(nbtId);
                if (nbt.isEmpty()) {
                    kit.problems.add(name + ": no " + name + ".nbt next to the .json");
                    continue;
                }
                CompoundTag tag;
                try (InputStream in = nbt.get().open()) {
                    tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                }
                kit.parts.add(read(kit, jsonId.getNamespace() + ":" + name, json, tag));
            } catch (Exception ex) {
                kit.problems.add(name + ": " + ex.getMessage());
                ProjectRedacted.LOG.warn("Couldn't load site part {}: {}", jsonId, ex.toString());
            }
        }
        active = kit;
        if (!kit.parts.isEmpty()) ProjectRedacted.LOG.info("Loaded {} site part(s)", kit.parts.size());
        for (String p : kit.problems) ProjectRedacted.LOG.warn("Site parts: {}", p);
    }

    /** Loads an exported kit folder (§7.1), e.g. for /site parts test. */
    public static PartKit loadFolder(String name, Path dir) {
        PartKit kit = new PartKit(name);
        if (!Files.isDirectory(dir)) {
            kit.problems.add("No kit folder at " + dir);
            return kit;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path json : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String id = json.getFileName().toString().replace(".json", "");
                Path nbt = json.resolveSibling(id + ".nbt");
                try (Reader reader = Files.newBufferedReader(json)) {
                    JsonObject o = GSON.fromJson(reader, JsonObject.class);
                    if (!Files.exists(nbt)) {
                        kit.problems.add(id + ": no " + id + ".nbt next to the .json");
                        continue;
                    }
                    kit.parts.add(read(kit, id, o, NbtIo.readCompressed(nbt, NbtAccounter.unlimitedHeap())));
                } catch (Exception ex) {
                    kit.problems.add(id + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            kit.problems.add("Couldn't read " + dir + ": " + ex.getMessage());
        }
        WORLD_KITS.put(name, kit);
        return kit;
    }

    /** Builds a part from its .json settings and .nbt blocks, detecting doorways and markers from the blocks. */
    public static SitePart read(PartKit kit, String id, JsonObject json, CompoundTag tag) {
        SitePart p = new SitePart();
        p.id = id;
        p.kit = kit.name;
        p.kind = SitePart.Kind.parse(str(json, "kind", "room"));
        p.roomType = normalize(str(json, "room_type", null));
        p.weight = json.has("weight") ? Math.max(1, json.get("weight").getAsInt()) : 10;
        p.template = PartTemplate.parse(tag, BuiltInRegistries.BLOCK);
        p.sx = p.template.sx;
        p.sy = p.template.sy;
        p.sz = p.template.sz;
        for (PartTemplate.Entry e : p.template.blocks) {
            BlockState s = e.state();
            if (s.is(Blocks.JIGSAW)) {
                var orientation = s.getValue(JigsawBlock.ORIENTATION);
                CompoundTag nbt = e.nbt() == null ? new CompoundTag() : e.nbt();
                String type = nbt.getStringOr("name", "");
                if (type.contains(":")) type = type.substring(type.indexOf(':') + 1);
                if (type.isEmpty()) type = nbt.getStringOr("target", "");
                if (type.contains(":")) type = type.substring(type.indexOf(':') + 1);
                Direction top = orientation.front().getAxis() == Direction.Axis.Y ? orientation.top() : Direction.UP;
                String turnsInto = nbt.getStringOr("final_state", "minecraft:air");
                p.connectors.add(new SitePart.Connector(e.pos(), orientation.front(), type, top, turnsInto));
            } else if (s.is(Blocks.STRUCTURE_BLOCK) && e.nbt() != null && "DATA".equalsIgnoreCase(e.nbt().getStringOr("mode", ""))) {
                p.markers.add(new SitePart.Marker(e.pos(), e.nbt().getStringOr("metadata", "").trim()));
            } else if (s.is(ModBlocks.MAINFRAME) || s.is(ModBlocks.RUINED_MAINFRAME)) {
                p.mainframe = e.pos();
            } else if (s.is(ModBlocks.SITE_DIRECTOR_COMPUTER)) {
                p.terminal = e.pos();
            } else if (s.is(ModBlocks.CASSIE) || s.is(ModBlocks.RUINED_CASSIE)) {
                p.cassie = e.pos();
            } else if (s.is(ModBlocks.KEYCARD_READER)) {
                p.readers.add(e.pos());
            }
        }
        if (json.has("rooms")) {
            for (JsonElement el : json.getAsJsonArray("rooms")) {
                JsonObject r = el.getAsJsonObject();
                String type = normalize(str(r, "type", null));
                if (type == null) continue;
                JsonArray from = r.getAsJsonArray("from"), to = r.getAsJsonArray("to");
                Direction door = Direction.byName(str(r, "door", "north"));
                p.rooms.add(new SitePart.PartRoom(type, from.get(0).getAsInt(), from.get(1).getAsInt(), from.get(2).getAsInt(),
                        to.get(0).getAsInt(), to.get(1).getAsInt(), to.get(2).getAsInt(), SitePart.side(door == null ? Direction.NORTH : door)));
            }
        }
        if (p.rooms.isEmpty() && p.roomType != null) {
            int doorSide = 0;
            for (SitePart.Connector c : p.connectors) {
                if (!c.vertical()) {
                    doorSide = SitePart.side(c.facing());
                    break;
                }
            }
            p.rooms.add(new SitePart.PartRoom(p.roomType, 0, 0, 0, p.sx - 1, p.sy - 1, p.sz - 1, doorSide));
        }
        if (p.kind == SitePart.Kind.ENTRANCE && p.roomType == null) p.roomType = RoomTypes.ENTRANCE;
        return p;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    /** Accepts "barracks" as well as "projectredacted:barracks". */
    public static String normalize(String type) {
        if (type == null || type.isBlank()) return null;
        return type.contains(":") ? type : ProjectRedacted.MOD_ID + ":" + type;
    }

    // ---------------------------------------------------------------- the check report (§7.1)

    /** Everything about this part that breaks the building standard (§3). Empty when it's fine. */
    public static List<String> check(SitePart p) {
        List<String> out = new ArrayList<>();
        if (p.sy % SitePart.STOREY != 0) out.add("height is " + p.sy + "; parts are 6 blocks per storey (6, 12, 18...)");
        int halls = 0, doors = 0, ups = 0, downs = 0, exits = 0;
        for (SitePart.Connector c : p.connectors) {
            switch (c.type()) {
                case SitePart.HALL -> halls++;
                case SitePart.DOOR -> doors++;
                case SitePart.STAIR_UP -> ups++;
                case SitePart.STAIR_DOWN -> downs++;
                case SitePart.SURFACE_EXIT -> exits++;
                default -> out.add("doorway at " + c.pos().toShortString() + " has an unknown name \"" + c.type() + "\"");
            }
            if (c.vertical()) {
                int expected = c.facing() == Direction.UP ? SitePart.STOREY - 1 : 0;
                if (Math.floorMod(c.pos().getY(), SitePart.STOREY) != expected)
                    out.add(c.type() + " at " + c.pos().toShortString() + " should be in the " + (c.facing() == Direction.UP ? "ceiling" : "floor"));
            } else {
                if (Math.floorMod(c.pos().getY(), SitePart.STOREY) != 1)
                    out.add(c.type() + " at " + c.pos().toShortString() + " is at the wrong height; doorways start one block above the floor");
                boolean onWall = switch (c.facing()) {
                    case NORTH -> c.pos().getZ() == 0;
                    case SOUTH -> c.pos().getZ() == p.sz - 1;
                    case WEST -> c.pos().getX() == 0;
                    default -> c.pos().getX() == p.sx - 1;
                };
                if (!onWall) out.add(c.type() + " at " + c.pos().toShortString() + " isn't in the outer wall facing " + c.facing().getName());
            }
        }
        switch (p.kind) {
            case HALLWAY -> {
                if (halls + doors < 2) out.add("a hallway needs at least two openings");
            }
            case STAIRWELL -> {
                if (ups + downs == 0) out.add("a stairwell needs a stair_up or stair_down landing");
                if (halls + doors == 0) out.add("a stairwell needs a hall opening on its side");
            }
            case ENTRANCE -> {
                if (exits == 0) out.add("the Entrance needs a surface_exit");
                if (halls + doors == 0) out.add("the Entrance needs a hall or door going inward");
            }
            case ROOM -> {
                if (halls + doors == 0) out.add("the room has no doorway");
                if (p.roomTypes().isEmpty()) out.add("no room type: set one in the frame or register the rooms inside");
                for (String t : p.roomTypes()) if (RoomTypes.get(t) == null) out.add("unknown room type " + t);
            }
        }
        if (p.roomTypes().contains(RoomTypes.DIRECTORS_OFFICE)) {
            if (p.mainframe == null) out.add("the Director's Office has no Mainframe");
            if (p.terminal == null) out.add("the Director's Office has no Site Director's Computer");
        }
        return out;
    }

    /** Level 0 parts the kit is missing (they come from the placeholder kit until added). */
    public List<String> missingLevel0() {
        List<String> out = new ArrayList<>();
        if (!hasOwnPart(RoomTypes.DIRECTORS_OFFICE)) out.add("Director's Office");
        if (!hasOwnPart(RoomTypes.BARRACKS)) out.add("Barracks");
        boolean entrance = false, hallway = false, stairwell = false;
        for (SitePart p : parts) {
            entrance |= p.kind == SitePart.Kind.ENTRANCE;
            hallway |= p.kind == SitePart.Kind.HALLWAY;
            stairwell |= p.kind == SitePart.Kind.STAIRWELL;
        }
        if (!entrance) out.add("Entrance");
        if (!hallway) out.add("Hallway");
        if (!stairwell) out.add("Stairwell");
        return out;
    }

    /** Positions are local; used by the export to show where a problem is. */
    public static BlockPos local(BlockPos world, BlockPos min) {
        return world.subtract(min);
    }
}
