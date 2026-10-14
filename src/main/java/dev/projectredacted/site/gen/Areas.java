package dev.projectredacted.site.gen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.DoorControl;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.SiteType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.Reader;
import java.util.*;

/**
 * Areas (site generation v4, §2): Entrance, Admin, Living, Research, Containment and Engineering, plus any a
 * datapack adds in data/&lt;ns&gt;/projectredacted/site_areas/. Each room type belongs to one area, and an area
 * only holds rooms of its own kind. A site can have several areas of a kind (Living 1, Living 2); each is
 * entered through its checkpoint and grows a main hall with side halls (§3).
 */
public final class Areas {
    /** Where an area sits by site type (SG §4): its storey on layered sites. */
    public enum Layer {
        SURFACE, ADMINISTRATIVE, RESEARCH, CONTAINMENT, INFRASTRUCTURE;

        public static Layer parse(String s, Layer fallback) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }

    /** How areas join each other (§4.1). */
    public enum Layout {
        HUB("Central hub", "A hub next to the Admin area; every other area's checkpoint opens onto one of its arms."),
        SPINE("Central spine", "A long spine corridor from the Admin checkpoint; area checkpoints open off both sides."),
        SECTORS("Sectors", "Areas sit apart, joined by tunnels that end at their checkpoints. Slow to walk, hard to raid.");

        public final String displayName, description;

        Layout(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /** An area kind. */
    public static class Area {
        public String id;
        public String name;
        public Layer layer = Layer.ADMINISTRATIVE;
        public String color = "white";
        public int clearance = 1;
        /** Fill limits (§3.1): main hall pieces, side halls, pieces per side hall. */
        public int mainHall = 8, sideHalls = 6, sideHall = 4;
        public final List<String> roomTypes = new ArrayList<>();
    }

    /** One area in a site (saved with the site), keyed like "living-2". */
    public static class AreaState {
        public String kind;
        public int number;
        /** The Director's name for it, or null for "Living" / "Living 2". */
        public String name;
        /** Director's override, or -1 for the area's default. */
        public int clearance = -1;
        public int nextRoomNo = 1;
    }

    public static final String ROLE_HUB = "hub", ROLE_LINK = "link", ROLE_SPINE = "spine", ROLE_TUNNEL = "tunnel",
            ROLE_CHECKPOINT = "checkpoint", ROLE_MAIN_HALL = "main_hall", ROLE_SIDE_HALL = "side_hall", ROLE_ROOM = "room";
    /** Roles of parts that belong to an area. */
    public static final Set<String> AREA_ROLES = Set.of(ROLE_CHECKPOINT, ROLE_MAIN_HALL, ROLE_SIDE_HALL, ROLE_ROOM);
    public static final String ENTRANCE = "entrance", ADMIN = "admin", LIVING = "living", RESEARCH = "research",
            CONTAINMENT = "containment", ENGINEERING = "engineering", SHARED = "shared";

    private static final Map<String, Area> AREAS = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    static {
        builtIns();
    }

    private static void builtIns() {
        AREAS.clear();
        add(ENTRANCE, "Entrance", Layer.SURFACE, "red", 1, "entrance", "hallway_checkpoint", "transition_checkpoint", "armory", "prison",
                "security_office", "interrogation_room", "visitor_screening", "emergency_exit", "loading_dock");
        add(ADMIN, "Admin", Layer.ADMINISTRATIVE, "gold", 2, "directors_office", "admin_office", "conference_room", "records_room", "hr_office",
                "ethics_committee_office");
        add(LIVING, "Living", Layer.ADMINISTRATIVE, "green", 1, "barracks", "single_bedroom", "cafeteria", "kitchen", "lounge", "bathroom",
                "medbay", "checkup_room", "waiting_room", "showers", "laundry");
        add(RESEARCH, "Research", Layer.RESEARCH, "blue", 2, "lab", "researcher_office", "testing_chamber", "sample_storage", "equipment_workshop");
        add(CONTAINMENT, "Containment", Layer.CONTAINMENT, "amber", 3, "light_cell", "medium_cell", "heavy_cell", "observation_room",
                "safe_storage_chamber", "sample_vault", "security_station", "isolation_room");
        add(ENGINEERING, "Engineering", Layer.INFRASTRUCTURE, "gray", 1, "generator", "water_pump", "circuit_breaker", "storage_room",
                "supply_depot", "ventilation", "server_room", "maintenance_workshop", "waste_management");
    }

    private static void add(String id, String name, Layer layer, String color, int clearance, String... rooms) {
        Area a = new Area();
        a.id = id;
        a.name = name;
        a.layer = layer;
        a.color = color;
        a.clearance = clearance;
        for (String r : rooms) a.roomTypes.add(ProjectRedacted.MOD_ID + ":" + r);
        AREAS.put(id, a);
    }

    public static Area get(String kind) {
        return kind == null ? null : AREAS.get(kind.toLowerCase(Locale.ROOT));
    }

    public static Collection<Area> all() {
        return AREAS.values();
    }

    /**
     * Reloads datapack areas from data/&lt;ns&gt;/projectredacted/site_areas/*.json: id, name, layer, color, clearance,
     * room_types, main_hall, side_halls, side_hall. A room type listed here moves to this area.
     */
    public static void reload(ResourceManager manager) {
        builtIns();
        Map<Identifier, Resource> found = manager.listResources("projectredacted/site_areas", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String file = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject o = GSON.fromJson(reader, JsonObject.class);
                String id = (o.has("id") ? o.get("id").getAsString() : file).trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty() || id.equals(SHARED)) continue;
                Area a = AREAS.getOrDefault(id, new Area());
                a.id = id;
                if (o.has("name")) a.name = o.get("name").getAsString();
                if (a.name == null) a.name = id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
                if (o.has("layer")) a.layer = Layer.parse(o.get("layer").getAsString(), a.layer);
                if (o.has("color")) a.color = o.get("color").getAsString();
                if (o.has("clearance")) a.clearance = Math.max(0, Math.min(5, o.get("clearance").getAsInt()));
                if (o.has("main_hall")) a.mainHall = Math.max(1, o.get("main_hall").getAsInt());
                if (o.has("side_halls")) a.sideHalls = Math.max(0, o.get("side_halls").getAsInt());
                if (o.has("side_hall")) a.sideHall = Math.max(1, o.get("side_hall").getAsInt());
                if (o.has("room_types")) {
                    a.roomTypes.clear();
                    for (JsonElement t : o.getAsJsonArray("room_types")) {
                        String type = PartKit.normalize(t.getAsString());
                        if (type == null) continue;
                        for (Area other : AREAS.values()) other.roomTypes.remove(type);
                        a.roomTypes.add(type);
                    }
                }
                AREAS.put(id, a);
            } catch (Exception ex) {
                ProjectRedacted.LOG.warn("Couldn't load site area {}: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- which area

    /** The area kind a room type belongs to: its own {@code area} field, the area lists, then its category. */
    public static String areaOf(String typeId) {
        RoomType t = RoomTypes.get(typeId);
        if (t != null && t.area != null && get(t.area) != null) return t.area.toLowerCase(Locale.ROOT);
        for (Area a : AREAS.values()) if (a.roomTypes.contains(typeId)) return a.id;
        String category = t == null ? "Custom" : t.category;
        return switch (category) {
            case "Core", "Offices" -> ADMIN;
            case "Engineering" -> ENGINEERING;
            case "Research" -> RESEARCH;
            case "Containment" -> CONTAINMENT;
            case "Checkpoint", "Security" -> ENTRANCE;
            default -> LIVING;
        };
    }

    /** "living" for "living-2". */
    public static String kindOf(String key) {
        if (key == null) return null;
        int dash = key.lastIndexOf('-');
        return dash < 0 ? key : key.substring(0, dash);
    }

    public static int numberOf(String key) {
        int dash = key == null ? -1 : key.lastIndexOf('-');
        try {
            return dash < 0 ? 1 : Integer.parseInt(key.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static String key(String kind, int number) {
        return kind + "-" + number;
    }

    public static AreaState state(Site site, String key) {
        AreaState s = site.areas.get(key);
        if (s == null) {
            s = new AreaState();
            s.kind = kindOf(key);
            s.number = numberOf(key);
            site.areas.put(key, s);
        }
        return s;
    }

    /** The site's areas of one kind, oldest first. */
    public static List<String> instances(Site site, String kind) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, AreaState> e : site.areas.entrySet()) if (kind.equals(e.getValue().kind)) out.add(e.getKey());
        out.sort(Comparator.comparingInt(Areas::numberOf));
        return out;
    }

    /** The key a new area of this kind gets. */
    public static String next(Site site, String kind) {
        int n = 1;
        for (String k : instances(site, kind)) n = Math.max(n, numberOf(k) + 1);
        return key(kind, n);
    }

    /** "Living", "Living 2", or the Director's name for it. */
    public static String displayName(Site site, String key) {
        if (key == null) return "";
        AreaState s = site == null ? null : site.areas.get(key);
        if (s != null && s.name != null && !s.name.isBlank()) return s.name;
        Area a = get(kindOf(key));
        String base = a == null ? kindOf(key) : a.name;
        boolean several = site != null && instances(site, kindOf(key)).size() > 1;
        return several || numberOf(key) > 1 ? base + " " + numberOf(key) : base;
    }

    public static Layout layout(Site site) {
        if (site.connection != null) return site.connection;
        return site.type == SiteType.UNDERGROUND ? Layout.SPINE : Layout.HUB;
    }

    // ---------------------------------------------------------------- where areas go

    /** The storey a layer sits on for this site type, or null if it can go anywhere. */
    public static Integer storey(Site site, Layer layer) {
        return switch (site.type) {
            case UNDERGROUND -> switch (layer) {
                case SURFACE -> Math.max(0, SiteTypeRules.surfaceStorey(site.type) - 1);
                case ADMINISTRATIVE -> 0;
                case RESEARCH -> -1;
                case CONTAINMENT -> -2;
                case INFRASTRUCTURE -> -3;
            };
            case SURFACE_TALL -> switch (layer) {
                case INFRASTRUCTURE -> -1;
                case SURFACE -> 0;
                case ADMINISTRATIVE -> 1;
                case RESEARCH -> 2;
                case CONTAINMENT -> 3;
            };
            case SURFACE_FLAT -> layer == Layer.CONTAINMENT && site.containmentBelow ? -1 : 0;
        };
    }

    /** The next storey further from the surface, when a layer's own storey is full. */
    public static int further(Site site, int storey) {
        return site.type == SiteType.UNDERGROUND ? storey - 1 : site.type == SiteType.SURFACE_TALL ? storey + 1 : storey;
    }

    /** Directions areas grow in, in degrees from the middle of the site toward the Entrance. */
    private static double slotAngle(String kind) {
        return switch (kind) {
            case ENTRANCE -> 0;
            case LIVING -> -70;
            case RESEARCH -> 90;
            case ENGINEERING -> -130;
            case CONTAINMENT -> 180;
            case ADMIN -> 45;
            default -> 180 + 37 * (Math.floorMod(kind.hashCode(), 7) - 3);
        };
    }

    /** The middle of the space an area grows into: its direction from the layout's centre, on its layer's storey. */
    public static BlockPos slot(Site site, String kind, Box bounds, int baseFloor) {
        BlockPos hub = layoutCenter(site);
        Room entrance = site.firstEntrance();
        double toward = 0;
        if (entrance != null) toward = Math.atan2(entrance.center().getZ() - hub.getZ(), entrance.center().getX() - hub.getX());
        double a = toward + Math.toRadians(slotAngle(kind));
        double radius = 0.6 * Math.min(bounds.x2() - bounds.x1(), bounds.z2() - bounds.z1()) / 2.0;
        Area def = get(kind);
        Integer storey = def == null ? null : storey(site, def.layer);
        int y = baseFloor + SitePart.STOREY * (storey == null ? 0 : storey);
        return new BlockPos(hub.getX() + (int) Math.round(Math.cos(a) * radius), y, hub.getZ() + (int) Math.round(Math.sin(a) * radius));
    }

    private static BlockPos layoutCenter(Site site) {
        for (PlacedPart p : site.parts) {
            if (p.removed || !(ROLE_HUB.equals(p.role) || ROLE_SPINE.equals(p.role) || ROLE_TUNNEL.equals(p.role))) continue;
            Box b = p.box();
            if (b != null) return b.center();
        }
        return site.center();
    }

    // ---------------------------------------------------------------- rooms and halls

    /** Gives a room its area and number: the area whose parts it sits among, or the first area of its kind. */
    public static void assign(Site site, Room room) {
        if (room.area == null || kindOf(room.area).equals(room.area)) {
            String inside = areaAt(site, room.center());
            String kind = areaOf(room.type);
            if (inside == null || !kindOf(inside).equals(kind)) {
                List<String> have = instances(site, kind);
                inside = have.isEmpty() ? key(kind, 1) : have.get(0);
            }
            room.area = inside;
        }
        AreaState s = state(site, room.area);
        if (room.areaNo <= 0) room.areaNo = s.nextRoomNo++;
        room.areaName = displayName(site, room.area);
    }

    /** The area whose halls, checkpoint or rooms are at (or within 4 blocks of) this spot, or null. */
    public static String areaAt(Site site, BlockPos pos) {
        for (PlacedPart p : site.parts) {
            if (p.removed || p.area == null) continue;
            Box b = p.box();
            if (b != null && b.grow(4).contains(pos)) return p.area;
        }
        return null;
    }

    /** Refreshes the area names rooms show in their labels (after a rename or a second area of a kind). */
    public static void relabel(Site site) {
        for (Room r : site.rooms) if (r.area != null) r.areaName = displayName(site, r.area);
    }

    public static int rooms(Site site, String key) {
        int n = 0;
        for (Room r : site.rooms) if (key.equals(r.area)) n++;
        return n;
    }

    /** Pieces of the main hall (hall 0) or of side hall {@code hall}. */
    public static int hallLength(Site site, String key, String role, int hall) {
        int n = 0;
        for (PlacedPart p : site.parts) if (!p.removed && key.equals(p.area) && role.equals(p.role) && (ROLE_MAIN_HALL.equals(role) || p.hall == hall)) n++;
        return n;
    }

    /** Side hall numbers in use. */
    public static SortedSet<Integer> sideHalls(Site site, String key) {
        SortedSet<Integer> out = new TreeSet<>();
        for (PlacedPart p : site.parts) if (!p.removed && key.equals(p.area) && ROLE_SIDE_HALL.equals(p.role)) out.add(p.hall);
        return out;
    }

    /** Full once its main hall and side halls have reached their limits (§3.1). */
    public static boolean full(Site site, String key) {
        Area a = get(kindOf(key));
        if (a == null) return false;
        if (hallLength(site, key, ROLE_MAIN_HALL, 0) < a.mainHall) return false;
        SortedSet<Integer> halls = sideHalls(site, key);
        if (halls.size() < a.sideHalls) return false;
        for (int h : halls) if (hallLength(site, key, ROLE_SIDE_HALL, h) < a.sideHall) return false;
        return true;
    }

    // ---------------------------------------------------------------- clearance (D15)

    /** An area's clearance: the Director's setting, or the area's default. Its checkpoint checks it. */
    public static int clearance(Site site, String key) {
        AreaState s = site.areas.get(key);
        if (s != null && s.clearance >= 0) return s.clearance;
        return defaultClearance(kindOf(key));
    }

    public static int defaultClearance(String kind) {
        Area a = get(kind);
        return a == null ? 1 : a.clearance;
    }

    /** The clearance of the first area of a kind, or that kind's default. */
    public static int kindClearance(Site site, String kind) {
        List<String> have = instances(site, kind);
        return have.isEmpty() ? defaultClearance(kind) : clearance(site, have.get(0));
    }

    /**
     * Sets every checkpoint reader to the clearance of the area it lets you into (D15) and remembers which way it
     * faces, so a reader only answers people standing in front of it. Run when a part or room finishes and when
     * the Director changes a clearance.
     */
    public static void applyReaders(ServerLevel level, Site site) {
        registerGates(site);
        for (PlacedPart p : site.parts) {
            if (p.removed || !p.built) continue;
            SitePart part = p.part();
            if (part == null || part.kind != SitePart.Kind.GATE) continue;
            labelSigns(level, site, p);
            int inner = p.area == null ? 1 : clearance(site, p.area);
            for (BlockPos local : part.readers) {
                BlockPos w = PartBuilder.world(p, local);
                if (!level.isLoaded(w)) continue;
                BlockState s = level.getBlockState(w);
                if (!s.is(ModBlocks.KEYCARD_READER)) continue;
                int want = Math.max(1, Math.min(5, inward(part, p, local, s) ? inner : 1));
                if (s.getValue(KeycardReaderBlock.LEVEL) != want) level.setBlock(w, s.setValue(KeycardReaderBlock.LEVEL, want), 3);
                SiteManager.addReader(w);
            }
        }
    }

    /** Fills blank signs in a checkpoint: "CONTAINMENT / CHECKPOINT". Signs with text are left alone. */
    private static void labelSigns(ServerLevel level, Site site, PlacedPart p) {
        Box b = p.box();
        if (b == null || p.area == null) return;
        Component top = Component.literal(displayName(site, p.area).toUpperCase(Locale.ROOT));
        Component bottom = Component.literal("CHECKPOINT");
        for (BlockPos pos : BlockPos.betweenClosed(b.x1(), b.y1(), b.z1(), b.x2(), b.y2(), b.z2())) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            if (sign.getText(SignTextSlot.FRONT).hasMessage(false)) continue;
            sign.updateText(t -> t.asMutable().setLine(1, top).setLine(2, bottom).asImmutable(), SignTextSlot.FRONT);
        }
    }

    /** Whether passing this reader's door takes you into the checkpoint's area. */
    private static boolean inward(SitePart part, PlacedPart p, BlockPos local, BlockState state) {
        Direction facing = state.getValue(dev.projectredacted.block.FacingBlock.FACING);
        BlockPos w = PartBuilder.world(p, local);
        SitePart.Connector entry = part.worldConnector(Math.max(0, Math.min(part.connectors.size() - 1, p.entry)), p.origin, p.rot);
        BlockPos front = w.relative(facing, 2), back = w.relative(facing.getOpposite(), 2);
        boolean towardEntry = front.distSqr(entry.pos()) < back.distSqr(entry.pos());
        // Built from inside (the Admin checkpoint grows out of the office), its entry is the area side.
        return p.entryInside != towardEntry;
    }

    /** Tells the door control which readers are one-sided and which are locked down. No world access. */
    public static void registerGates(Site site) {
        for (PlacedPart p : site.parts) {
            if (p.removed) continue;
            SitePart part = p.part();
            if (part == null || part.kind != SitePart.Kind.GATE) continue;
            boolean locked = p.area != null && site.lockdown.contains(p.area);
            for (BlockPos local : part.readers) {
                BlockPos w = PartBuilder.world(p, local);
                DoorControl.setOneSided(w, true);
                DoorControl.setLocked(w, locked);
            }
        }
        for (BlockPos hatch : site.hatchReaders) DoorControl.setFromBelow(hatch);
    }

    // ---------------------------------------------------------------- lockdown

    /** Closes the area's checkpoint and stops its readers until the lockdown is lifted. */
    public static void lockdown(ServerLevel level, Site site, String key) {
        if (key == null || !site.lockdown.add(key)) return;
        registerGates(site);
        for (PlacedPart p : site.parts) {
            if (p.removed || !key.equals(p.area)) continue;
            SitePart part = p.part();
            Box b = p.box();
            if (part == null || b == null || part.kind != SitePart.Kind.GATE) continue;
            DoorControl.setDoorsInBox(level, new AABB(b.x1(), b.y1(), b.z1(), b.x2() + 1, b.y2() + 1, b.z2() + 1), false);
        }
        String name = displayName(site, key);
        site.log("Lockdown: " + name + ".");
        if (!Cassie.announce(site, "Lockdown: " + name + ". All personnel remain in place.")) Cassie.alarm(site, "Lockdown: " + name);
    }

    public static void lift(ServerLevel level, Site site, String key) {
        if (!site.lockdown.remove(key)) return;
        registerGates(site);
        String name = displayName(site, key);
        site.log("Lockdown lifted: " + name + ".");
        if (!Cassie.announce(site, "Lockdown lifted: " + name + ".")) Cassie.broadcast(site, Component.literal("Lockdown lifted: " + name + ".").withStyle(ChatFormatting.GREEN), false);
    }

    private Areas() {}
}
