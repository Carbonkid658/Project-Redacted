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

    /**
     * Pieces for one step of the layout (v4 §5.1): of this kind and tag (null for untagged), this area's own if it
     * has any (V6: every area has its own look), else shared ones, else the placeholders.
     */
    public List<SitePart> pieces(SitePart.Kind kind, String tag, String area) {
        List<SitePart> own = new ArrayList<>(), shared = new ArrayList<>();
        for (SitePart p : parts) {
            // A hallway with a link belongs to its room (§5.6) and only comes with it.
            if (p.kind != kind || !tagOk(p, tag) || p.hasLinks()) continue;
            if (area != null && area.equals(p.area)) own.add(p);
            else if (p.area == null) shared.add(p);
        }
        if (!own.isEmpty()) return own;
        if (!shared.isEmpty()) return shared;
        List<SitePart> out = new ArrayList<>();
        for (SitePart p : placeholders()) if (p.kind == kind && tagOk(p, tag)) out.add(p);
        return out;
    }

    private static boolean tagOk(SitePart p, String tag) {
        return tag == null ? p.tags.isEmpty() : p.tagged(tag);
    }

    /** Parts that contain this room type, not counting SCP-specific rooms; the placeholder sizes if the kit has none (§2.8). */
    public List<SitePart> forRoom(String roomType) {
        return forRoom(roomType, Set.of());
    }

    /** As {@link #forRoom(String)}, plus the SCP-specific rooms for the SCPs in {@code scps} (V11). */
    public List<SitePart> forRoom(String roomType, Set<String> scps) {
        List<SitePart> out = new ArrayList<>();
        for (SitePart p : parts) {
            if (p.kind == SitePart.Kind.ENTRANCE || !p.roomTypes().contains(roomType)) continue;
            if (p.scp == null || scps.contains(p.scp)) out.add(p);
        }
        if (out.stream().anyMatch(p -> p.scp == null)) return out;
        for (SitePart p : placeholders()) if (p.kind == SitePart.Kind.ROOM && roomType.equals(p.roomType)) out.add(p);
        if (out.stream().anyMatch(p -> p.scp == null)) return out;
        RoomType t = RoomTypes.get(roomType);
        if (t == null) return out;
        for (SitePart p : ProceduralParts.roomSizes(t)) out.add(intern(p));
        return out;
    }

    /**
     * What can go on the other end of {@code from}'s link (§5.5, §5.6): the exact build for link_exact, else every
     * variant of the linked room type. Each candidate has a link of its own pointing back at {@code from}.
     */
    public List<SitePart> companions(SitePart from, SitePart.Link link) {
        List<SitePart> out = new ArrayList<>();
        for (SitePart p : parts) if (p != from && link.accepts(p) && p.linksTo(from)) out.add(p);
        return out;
    }

    /**
     * Parts that bring {@code target} in as their companion and can join the halls themselves (§5.5 "only one
     * side needs a hall"): an Entrance with no hall opening reaches the site through its checkpoint room.
     */
    public List<SitePart> entriesFor(SitePart target) {
        List<SitePart> out = new ArrayList<>();
        if (!target.hasLinks()) return out;
        for (SitePart p : parts) if (p != target && !p.companionOnly() && p.linksTo(target) && target.linksTo(p)) out.add(p);
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

    private static synchronized SitePart intern(SitePart p) {
        return PLACEHOLDERS.computeIfAbsent(p.id, k -> p);
    }

    public static synchronized SitePart placeholder(String id) {
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

    /** Synchronized: ruins are planned on world generation threads. */
    private static synchronized Collection<SitePart> placeholders() {
        if (PLACEHOLDERS.isEmpty()) for (SitePart p : ProceduralParts.level0Kit()) PLACEHOLDERS.put(p.id, p);
        List<SitePart> base = new ArrayList<>();
        for (SitePart p : PLACEHOLDERS.values()) if (p.kind != SitePart.Kind.ROOM || isLevel0Room(p)) base.add(p);
        return base;
    }

    private static boolean isLevel0Room(SitePart p) {
        return RoomTypes.DIRECTORS_OFFICE.equals(p.roomType) || RoomTypes.BARRACKS.equals(p.roomType) || "projectredacted:light_cell".equals(p.roomType);
    }

    /** Sizes on offer for a room type, smallest first, as "11×9" labels. */
    public List<SitePart> sizes(String roomType) {
        return sizes(roomType, Set.of());
    }

    /** As {@link #sizes(String)}, with the SCP-specific rooms for {@code scps} first, so they're picked by default (§5.3). */
    public List<SitePart> sizes(String roomType, Set<String> scps) {
        List<SitePart> list = new ArrayList<>(forRoom(roomType, scps));
        list.sort(Comparator.comparingInt((SitePart p) -> p.scp != null ? 0 : 1).thenComparingInt(p -> p.sx * p.sz));
        return list;
    }

    // ---------------------------------------------------------------- loading

    /** Where a world's structure block saves named site:... land (§5.5). */
    public static Path savesDir(Path generated) {
        return generated.resolve("site").resolve("structures");
    }

    /**
     * Reloads the default kit: structure saves named site:&lt;area&gt;/&lt;kind&gt;/&lt;name&gt; from datapacks
     * (data/site/structure/... or data/&lt;ns&gt;/structure/site/...), then the same saves in the world's
     * generated folder (so a build can be tested as soon as it's saved), then old-style part .json files.
     */
    public static void reload(ResourceManager manager, Path generated) {
        PartKit kit = new PartKit("default");
        Map<String, SitePart> named = new LinkedHashMap<>();
        Map<Identifier, Resource> structures = manager.listResources("structure", id -> id.getPath().endsWith(".nbt"));
        for (Map.Entry<Identifier, Resource> e : structures.entrySet()) {
            String path = e.getKey().getPath();
            String rel;
            if (e.getKey().getNamespace().equals("site")) rel = path.substring("structure/".length());
            else if (path.startsWith("structure/site/")) rel = path.substring("structure/site/".length());
            else continue;
            rel = rel.substring(0, rel.length() - 4);
            try (InputStream in = e.getValue().open()) {
                SitePart p = kit.readNamed(rel, NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
                if (p != null) named.put(p.id, p);
            } catch (Exception ex) {
                kit.problems.add(rel + ": " + ex.getMessage());
            }
        }
        if (generated != null) {
            Path dir = savesDir(generated);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path f : files.filter(f -> f.toString().endsWith(".nbt")).sorted().toList()) {
                        String rel = dir.relativize(f).toString().replace('\\', '/');
                        rel = rel.substring(0, rel.length() - 4);
                        try {
                            SitePart p = kit.readNamed(rel, NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()));
                            if (p != null) named.put(p.id, p);
                        } catch (Exception ex) {
                            kit.problems.add(rel + ": " + ex.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    kit.problems.add("Couldn't read " + dir + ": " + ex.getMessage());
                }
            }
        }
        kit.parts.addAll(named.values());
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

    private static final java.util.regex.Pattern WEIGHT = java.util.regex.Pattern.compile("_w(\\d+)$");

    /**
     * A part from a structure save named &lt;area&gt;/&lt;kind&gt;/&lt;name&gt; (v4 §5.1): the name says which area it's
     * for, what it is, and for rooms the room type. Null (with a problem noted) if the name doesn't fit.
     */
    public SitePart readNamed(String rel, CompoundTag tag) {
        String[] bits = rel.toLowerCase(Locale.ROOT).split("/");
        if (bits.length != 3) {
            problems.add(rel + ": save it as site:<area>/<kind>/<name>, e.g. site:living/room/barracks_a");
            return null;
        }
        String area = bits[0], kind = bits[1], name = bits[2];
        boolean shared = area.equals(Areas.SHARED);
        if (!shared && Areas.get(area) == null) {
            problems.add(rel + ": unknown area \"" + area + "\" (entrance, admin, living, research, containment, engineering or shared)");
            return null;
        }
        SitePart p = new SitePart();
        p.id = "site:" + area + "/" + kind + "/" + name;
        p.kit = this.name;
        p.area = shared ? null : area;
        var m = WEIGHT.matcher(name);
        if (m.find()) {
            p.weight = 10 * Math.max(1, Math.min(100, Integer.parseInt(m.group(1))));
            name = name.substring(0, m.start());
        }
        switch (kind) {
            case "room" -> p.kind = SitePart.Kind.ROOM;
            case "main_hall" -> {
                p.kind = SitePart.Kind.HALLWAY;
                p.tags.add(SitePart.TAG_MAIN_HALL);
            }
            case "side_hall" -> {
                p.kind = SitePart.Kind.HALLWAY;
                p.tags.add(SitePart.TAG_SIDE_HALL);
            }
            case "checkpoint" -> p.kind = SitePart.Kind.GATE;
            case "hub" -> p.kind = SitePart.Kind.HUB;
            case "spine" -> {
                p.kind = SitePart.Kind.HALLWAY;
                p.tags.add(SitePart.TAG_SPINE);
            }
            case "tunnel" -> {
                p.kind = SitePart.Kind.HALLWAY;
                p.tags.add(SitePart.TAG_TUNNEL);
            }
            case "hall" -> p.kind = SitePart.Kind.HALLWAY;
            case "stairwell" -> p.kind = SitePart.Kind.STAIRWELL;
            default -> {
                problems.add(rel + ": unknown kind \"" + kind + "\" (room, main_hall, side_hall, checkpoint; in shared: hub, spine, tunnel)");
                return null;
            }
        }
        scan(p, tag);
        if (p.kind == SitePart.Kind.ROOM) {
            String named;
            if (name.startsWith("scp_")) {
                String[] t = name.split("_");
                p.scp = t.length > 1 ? "scp_" + t[1] : null;
                named = cellFor(p.scp);
            } else {
                named = roomTypeFromName(name);
            }
            // The key blocks decide (§5.8): every closed space holding one is a room. The name is a fallback.
            List<SitePart.PartRoom> found = keyRooms(p);
            if (!found.isEmpty()) {
                p.rooms.addAll(found);
                p.roomType = named;
                if (named == null || found.stream().noneMatch(r -> r.type().equals(named))) p.roomType = found.get(0).type();
                for (SitePart.PartRoom r : found) if (p.scp == null && r.scp() != null) p.scp = r.scp();
            } else {
                p.roomType = named;
                if (p.roomType == null) {
                    problems.add(rel + ": no key block found, and the name doesn't start with a room type (e.g. barracks_small_a)");
                    return null;
                }
                p.rooms.add(new SitePart.PartRoom(p.roomType, 0, 0, 0, p.sx - 1, p.sy - 1, p.sz - 1, doorSide(p)));
            }
            if (p.roomType.equals(RoomTypes.ENTRANCE)) p.kind = SitePart.Kind.ENTRANCE;
        }
        return p;
    }

    /**
     * Room shape detection on a save (§5.7, §5.8): each key block's closed space becomes a room, with its real box
     * and the side its Room Entrance Marker faces. Spaces that leak, and spaces with two different key blocks, are
     * noted for the piece checker. Also works out the part's real shape for overlap checks.
     */
    static List<SitePart.PartRoom> keyRooms(SitePart p) {
        Map<Long, BlockState> blocks = new HashMap<>();
        for (PartTemplate.Entry e : p.template.blocks) if (!e.state().is(Blocks.STRUCTURE_VOID)) blocks.put(e.pos().asLong(), e.state());
        // Doorway openings are closed off, so a room doesn't leak out through its own door.
        Set<Long> openings = new HashSet<>();
        for (SitePart.Connector c : p.connectors) {
            if (c.vertical()) continue;
            Direction along = c.facing().getClockWise();
            int half = c.width() / 2;
            for (int k = -half; k <= half; k++) for (int y = 0; y < c.height(); y++) openings.add(c.pos().relative(along, k).above(y).asLong());
        }
        net.minecraft.world.level.BlockGetter none = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        dev.projectredacted.room.RoomShape.Grid grid = new dev.projectredacted.room.RoomShape.Grid() {
            @Override
            public dev.projectredacted.room.RoomShape.Cell at(BlockPos pos) {
                BlockState st = blocks.get(pos.asLong());
                if (st == null) return dev.projectredacted.room.RoomShape.Cell.OUTSIDE;
                if (openings.contains(pos.asLong()) || dev.projectredacted.room.RoomShape.isWall(none, BlockPos.ZERO, st)) return dev.projectredacted.room.RoomShape.Cell.WALL;
                BlockState b1 = blocks.get(pos.below().asLong()), b2 = blocks.get(pos.below(2).asLong());
                if (b1 != null && dev.projectredacted.room.RoomShape.isPlug(b1) || b2 != null && dev.projectredacted.room.RoomShape.isPlug(b2)) return dev.projectredacted.room.RoomShape.Cell.WALL;
                return dev.projectredacted.room.RoomShape.Cell.OPEN;
            }

            @Override
            public BlockState state(BlockPos pos) {
                return blocks.get(pos.asLong());
            }
        };
        List<SitePart.PartRoom> out = new ArrayList<>();
        List<dev.projectredacted.room.RoomShape.Result> spaces = new ArrayList<>();
        for (PartTemplate.Entry e : p.template.blocks) {
            dev.projectredacted.room.KeyBlocks.Key key = dev.projectredacted.room.KeyBlocks.of(e.state());
            if (key == null) continue;
            String where = e.state().getBlock().getName().getString() + " at " + e.pos().toShortString();
            dev.projectredacted.room.RoomShape.Result same = null;
            int sameIndex = -1;
            for (int i = 0; i < spaces.size(); i++) if (spaces.get(i).walls().contains(e.pos().asLong())) {
                same = spaces.get(i);
                sameIndex = i;
            }
            if (same != null) {
                if (!out.get(sameIndex).type().equals(key.roomType())) p.shapeProblems.add("two rooms in one space (the " + where + "): wall them off");
                continue;
            }
            dev.projectredacted.room.RoomShape.Result space = dev.projectredacted.room.RoomShape.fill(grid, dev.projectredacted.room.RoomShape.around(e.pos()));
            if (!space.closed()) {
                p.shapeProblems.add("the room with the " + where + " isn't closed" + (space.leak() == null ? "" : ": it leaks at " + space.leak().toShortString()));
                continue;
            }
            int[] b = space.box();
            int side = -1;
            for (SitePart.Connector c : p.connectors) {
                if (!c.type().equals(SitePart.DOOR) || c.vertical()) continue;
                if (space.contains(c.pos().relative(c.facing().getOpposite()))) side = SitePart.side(c.facing());
            }
            spaces.add(space);
            out.add(new SitePart.PartRoom(key.roomType(), b[0], b[1], b[2], b[3], b[4], b[5], side < 0 ? doorSide(p) : side, e.pos(), key.scp()));
        }
        p.solid = shape(p, grid);
        return out;
    }

    /**
     * The save's real shape: everything except the open air reachable from its outside (with doorways closed),
     * and structure void. Null when that's the whole box. Other parts may nest into the rest.
     */
    private static java.util.BitSet shape(SitePart p, dev.projectredacted.room.RoomShape.Grid grid) {
        java.util.BitSet outside = new java.util.BitSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (int x = 0; x < p.sx; x++) for (int y = 0; y < p.sy; y++) for (int z = 0; z < p.sz; z++) {
            boolean edge = x == 0 || y == 0 || z == 0 || x == p.sx - 1 || y == p.sy - 1 || z == p.sz - 1;
            BlockPos q = new BlockPos(x, y, z);
            var cell = grid.at(q);
            if (cell == dev.projectredacted.room.RoomShape.Cell.OUTSIDE) outside.set(p.cell(x, y, z));
            else if (edge && cell == dev.projectredacted.room.RoomShape.Cell.OPEN && grid.state(q).isAir()) {
                outside.set(p.cell(x, y, z));
                queue.add(q);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos q = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = q.relative(d);
                if (n.getX() < 0 || n.getY() < 0 || n.getZ() < 0 || n.getX() >= p.sx || n.getY() >= p.sy || n.getZ() >= p.sz) continue;
                int i = p.cell(n.getX(), n.getY(), n.getZ());
                if (outside.get(i) || grid.at(n) != dev.projectredacted.room.RoomShape.Cell.OPEN || !grid.state(n).isAir()) continue;
                outside.set(i);
                queue.add(n);
            }
        }
        if (outside.isEmpty()) return null;
        java.util.BitSet solid = new java.util.BitSet();
        solid.set(0, p.sx * p.sy * p.sz);
        solid.andNot(outside);
        return solid;
    }

    /** The longest room type id that starts the name and is followed by "_" or nothing: light_cell_large_b is a Light Cell. */
    static String roomTypeFromName(String name) {
        String best = null;
        for (RoomType t : RoomTypes.all()) {
            String path = t.id.substring(t.id.indexOf(':') + 1);
            if ((name.equals(path) || name.startsWith(path + "_")) && (best == null || path.length() > best.length() - best.indexOf(':') - 1)) best = t.id;
        }
        return best;
    }

    /** The weakest cell an SCP accepts (from its "Needs a medium cell or stronger" requirement), or a Light Cell. */
    public static String cellFor(String scp) {
        dev.projectredacted.api.ScpDefinition def = scpDefinition(scp);
        String cell = "light";
        if (def != null) {
            for (var r : def.requirements) {
                String d = r.describe().toLowerCase(Locale.ROOT);
                for (String c : new String[]{"light", "medium", "heavy"}) if (d.startsWith("needs a " + c + " cell")) cell = c;
            }
        }
        return ProjectRedacted.MOD_ID + ":" + cell + "_cell";
    }

    /** "scp_173" matches an SCP registered as scp_173 or numbered SCP-173. */
    public static dev.projectredacted.api.ScpDefinition scpDefinition(String scp) {
        if (scp == null) return null;
        for (var def : dev.projectredacted.api.ScpApi.all()) if (scpKey(def).equals(scp)) return def;
        return null;
    }

    public static String scpKey(dev.projectredacted.api.ScpDefinition def) {
        String id = def.id.contains(":") ? def.id.substring(def.id.indexOf(':') + 1) : def.id;
        id = id.toLowerCase(Locale.ROOT).replace('-', '_');
        if (id.startsWith("scp_")) return id.split("_").length > 1 ? "scp_" + id.split("_")[1] : id;
        return def.number == null ? id : def.number.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static int doorSide(SitePart p) {
        for (SitePart.Connector c : p.connectors) if (!c.vertical() && !c.isLink() && !c.type().equals(SitePart.SURFACE_EXIT)) return SitePart.side(c.facing());
        return 0;
    }

    /** Builds a part from its .json settings and .nbt blocks, detecting doorways and markers from the blocks. */
    public static SitePart read(PartKit kit, String id, JsonObject json, CompoundTag tag) {
        SitePart p = new SitePart();
        p.id = id;
        p.kit = kit.name;
        p.kind = SitePart.Kind.parse(str(json, "kind", "room"));
        p.roomType = normalize(str(json, "room_type", null));
        p.weight = json.has("weight") ? Math.max(1, json.get("weight").getAsInt()) : 10;
        String area = str(json, "area", null);
        p.area = area == null || area.isBlank() || Areas.get(area) == null ? null : area.trim().toLowerCase(Locale.ROOT);
        if (json.has("tags")) {
            JsonElement tags = json.get("tags");
            if (tags.isJsonArray()) for (JsonElement t : tags.getAsJsonArray()) p.tags.add(t.getAsString().trim().toLowerCase(Locale.ROOT));
            else for (String t : tags.getAsString().split(",")) if (!t.isBlank()) p.tags.add(t.trim().toLowerCase(Locale.ROOT));
        }
        scan(p, tag);
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
        if (p.rooms.isEmpty() && p.roomType != null) p.rooms.add(new SitePart.PartRoom(p.roomType, 0, 0, 0, p.sx - 1, p.sy - 1, p.sz - 1, doorSide(p)));
        if (p.kind == SitePart.Kind.ENTRANCE && p.roomType == null) p.roomType = RoomTypes.ENTRANCE;
        return p;
    }

    /**
     * Reads the blocks: jigsaws are doorways, Room Entrance Markers are a room's doors (they stay in the world),
     * Data structure blocks are markers, and the site machines and readers are noted.
     */
    private static void scan(SitePart p, CompoundTag tag) {
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
            } else if (s.is(ModBlocks.ROOM_ENTRANCE_MARKER)) {
                Direction f = s.getValue(dev.projectredacted.block.RoomEntranceMarkerBlock.FACING);
                p.connectors.add(new SitePart.Connector(e.pos(), f, SitePart.DOOR, Direction.UP, null));
                p.entranceMarkers++;
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
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    /** Accepts "barracks" as well as "projectredacted:barracks". */
    public static String normalize(String type) {
        if (type == null || type.isBlank()) return null;
        return type.contains(":") ? type : ProjectRedacted.MOD_ID + ":" + type;
    }

    // ---------------------------------------------------------------- the check report (§5.5)

    /** Everything about this part that breaks the building rules (v4 §5.2). Empty when it's fine. */
    public static List<String> check(SitePart p) {
        List<String> out = new ArrayList<>();
        out.addAll(p.shapeProblems);
        if (p.sy % SitePart.STOREY != 0) out.add("height is " + p.sy + "; builds are 6 blocks per storey (6, 12, 18...)");
        if (p.sx > SitePart.MAX_SIZE || p.sy > SitePart.MAX_SIZE || p.sz > SitePart.MAX_SIZE)
            out.add("it's " + p.sx + "×" + p.sy + "×" + p.sz + "; nothing can be bigger than 48×48×48");
        boolean gridded = p.kind == SitePart.Kind.HALLWAY || p.kind == SitePart.Kind.GATE || p.kind == SitePart.Kind.HUB;
        if (gridded && (p.sx % SitePart.GRID != 0 || p.sz % SitePart.GRID != 0))
            out.add("footprint is " + p.sx + "×" + p.sz + "; halls and checkpoints are multiples of 5 (5, 10, 15...)");
        int halls = 0, doors = 0, ups = 0, downs = 0, exits = 0, links = 0;
        for (SitePart.Connector c : p.connectors) {
            if (c.isLink()) {
                links++;
                SitePart.Link l = c.link();
                if (l.roomType() != null && RoomTypes.get(l.roomType()) == null) out.add("jigsaw at " + c.pos().toShortString() + " links to an unknown room type " + l.roomType());
                if (l.exactId() != null && l.exactId().split("/").length != 3) out.add("jigsaw at " + c.pos().toShortString() + " should be link_exact/<area>/<kind>/<name>");
                if (c.vertical()) out.add("link at " + c.pos().toShortString() + " faces up or down; links go in a wall, facing the other room");
                continue;
            }
            switch (c.type()) {
                case SitePart.HALL -> halls++;
                case SitePart.DOOR -> doors++;
                case SitePart.STAIR_UP -> ups++;
                case SitePart.STAIR_DOWN -> downs++;
                case SitePart.SURFACE_EXIT -> exits++;
                default -> out.add("jigsaw at " + c.pos().toShortString() + " has an unknown name \"" + c.type() + "\" (use hall, door, surface_exit, link/<room type>, link_optional/<room type> or link_exact/<area>/<kind>/<name>)");
            }
            if (c.vertical()) {
                int expected = c.facing() == Direction.UP ? SitePart.STOREY - 1 : 0;
                if (Math.floorMod(c.pos().getY(), SitePart.STOREY) != expected)
                    out.add(c.type() + " at " + c.pos().toShortString() + " should be in the " + (c.facing() == Direction.UP ? "ceiling" : "floor"));
                continue;
            }
            if (Math.floorMod(c.pos().getY(), SitePart.STOREY) != 1)
                out.add(c.type() + " at " + c.pos().toShortString() + " is at the wrong height; doorways start one block above the floor");
            boolean onWall = switch (c.facing()) {
                case NORTH -> c.pos().getZ() == 0;
                case SOUTH -> c.pos().getZ() == p.sz - 1;
                case WEST -> c.pos().getX() == 0;
                default -> c.pos().getX() == p.sx - 1;
            };
            if (!onWall) out.add(c.type() + " at " + c.pos().toShortString() + " isn't in the outer wall facing " + c.facing().getName());
            if (gridded && c.type().equals(SitePart.HALL)) {
                int along = c.facing().getAxis() == Direction.Axis.Z ? c.pos().getX() : c.pos().getZ();
                if (Math.floorMod(along, SitePart.GRID) != SitePart.GRID / 2)
                    out.add("hall opening at " + c.pos().toShortString() + " is off the grid; it should be in the middle of a 5-block cell (2, 7, 12...)");
            }
        }
        boolean named = p.id != null && p.id.startsWith("site:");
        switch (p.kind) {
            case HALLWAY -> {
                if (halls == 0) out.add("a hall needs at least one hall opening");
                if (p.tagged(SitePart.TAG_SPINE) || p.tagged(SitePart.TAG_TUNNEL)) {
                    if (p.area != null) out.add("spine and tunnel pieces go in shared");
                } else if (halls + doors + links < 2 && !p.tagged(SitePart.TAG_MAIN_HALL) && !p.tagged(SitePart.TAG_SIDE_HALL)) {
                    out.add("a hallway needs at least two openings");
                }
            }
            case STAIRWELL -> {
                if (ups + downs == 0) out.add("a stairwell needs a stair_up or stair_down landing");
                if (halls + doors == 0) out.add("a stairwell needs a hall opening on its side");
            }
            case ENTRANCE -> {
                if (exits == 0) out.add("the Entrance needs a surface_exit jigsaw on its outside");
                if (halls + doors == 0 && links == 0) out.add("the Entrance needs a hall opening going inward, or a link to its checkpoint room");
            }
            case HUB -> {
                if (halls < 3) out.add("a hub needs at least three hall openings (it has " + halls + ")");
            }
            case GATE -> {
                if (halls != 2) out.add("a checkpoint needs two hall openings, outside and inside (it has " + halls + ")");
                if (p.readers.isEmpty()) out.add("a checkpoint needs a Keycard Reader; its level is set from the area behind it");
            }
            case ROOM -> {
                // A room with only links comes as a companion (§5.5) and needs no entrance of its own.
                if (halls + doors == 0 && links == 0) out.add("the room has no entrance");
                if (named && p.entranceMarkers == 0 && links == 0) out.add("no Room Entrance Marker; put one in the doorway, arrow pointing out");
                if (named && p.rooms.stream().noneMatch(r -> r.key() != null)) out.add("no key block inside, so the name decides the room type; place the room's key block in it");
                if (p.roomTypes().isEmpty()) out.add("no room type");
                for (String t : p.roomTypes()) {
                    if (RoomTypes.get(t) == null) out.add("unknown room type " + t);
                    else if (p.area != null && !p.area.equals(Areas.areaOf(t)) && p.scp == null)
                        out.add(RoomTypes.get(t).name + " belongs in " + Areas.get(Areas.areaOf(t)).name + ", not " + Areas.get(p.area).name);
                }
                if (p.scp != null && scpDefinition(p.scp) == null) out.add("no SCP called " + p.scp + " is registered (yet)");
            }
        }
        for (String t : p.tags) if (!SitePart.TAGS.contains(t)) out.add("unknown tag \"" + t + "\"");
        if (p.roomTypes().contains(RoomTypes.DIRECTORS_OFFICE)) {
            if (p.mainframe == null) out.add("the Director's Office has no Mainframe");
            if (p.terminal == null) out.add("the Director's Office has no Site Director's Computer");
        }
        return out;
    }

    /** Links with nothing to join (§5.5, §5.6): no save of the linked type (or the exact one) has a link pointing back. */
    public List<String> linkProblems(SitePart p) {
        List<String> out = new ArrayList<>();
        for (SitePart.Connector c : p.connectors) {
            SitePart.Link l = c.isLink() ? c.link() : null;
            if (l == null || !companions(p, l).isEmpty()) continue;
            String what = l.exactId() != null ? "the save " + l.exactId() : "a " + (RoomTypes.get(l.roomType()) == null ? l.roomType() : RoomTypes.get(l.roomType()).name) + " save";
            out.add(c.type() + " at " + c.pos().toShortString() + ": there's no " + what + " with a link jigsaw pointing back"
                    + (l.required() ? ", so this room can't be placed" : ""));
        }
        return out;
    }

    /** Pieces of the starting set (§5.5) the kit doesn't have yet; the mod's own builder fills in for them. */
    public List<String> missingLevel0() {
        List<String> out = new ArrayList<>();
        String[][] areaRooms = {{Areas.ADMIN, "directors_office"}, {Areas.CONTAINMENT, "light_cell"}, {Areas.LIVING, "barracks"}};
        for (String[] ar : areaRooms) {
            String a = ar[0];
            if (!hasNamed(a, "room", ar[1])) out.add(a + "/room/" + ar[1] + "_*");
            if (!hasNamed(a, "checkpoint", "")) out.add(a + "/checkpoint/*");
            for (String shape : new String[]{"straight", "corner", "t"}) if (!hasNamed(a, "main_hall", shape)) out.add(a + "/main_hall/" + shape + "_*");
            for (String shape : new String[]{"straight", "corner"}) if (!hasNamed(a, "side_hall", shape)) out.add(a + "/side_hall/" + shape + "_*");
        }
        if (!hasNamed(Areas.ENTRANCE, "room", "entrance")) out.add("entrance/room/entrance_*");
        if (!hasNamed(Areas.SHARED, "hub", "")) out.add("shared/hub/*");
        for (String shape : new String[]{"straight", "t"}) if (!hasNamed(Areas.SHARED, "spine", shape)) out.add("shared/spine/" + shape + "_*");
        for (String shape : new String[]{"straight", "corner"}) if (!hasNamed(Areas.SHARED, "tunnel", shape)) out.add("shared/tunnel/" + shape + "_*");
        return out;
    }

    private boolean hasNamed(String area, String kind, String start) {
        String prefix = "site:" + area + "/" + kind + "/";
        for (SitePart p : parts) {
            if (!p.id.startsWith(prefix)) continue;
            String name = p.id.substring(prefix.length());
            if (start.isEmpty() || name.equals(start) || name.startsWith(start + "_")) return true;
        }
        return false;
    }

    /** Positions are local; used by the export to show where a problem is. */
    public static BlockPos local(BlockPos world, BlockPos min) {
        return world.subtract(min);
    }
}
