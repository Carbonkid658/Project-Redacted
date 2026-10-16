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
 * only holds rooms of its own kind. Areas set the clearance for their rooms and lock down together after a
 * breach.
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
                "security_office", "interrogation_room", "visitor_screening", "emergency_exit", "loading_dock", "field_operations");
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
                        String type = t.getAsString().isBlank() ? null : t.getAsString().contains(":") ? t.getAsString() : ProjectRedacted.MOD_ID + ":" + t.getAsString();
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

    // ---------------------------------------------------------------- rooms

    /** Gives a room its area and number: the first area of its room type's kind. Facilities are built by hand, so an area is simply every room of a kind. */
    public static void assign(Site site, Room room) {
        String kind = areaOf(room.type);
        if (room.area == null || !kind.equals(kindOf(room.area))) {
            List<String> have = instances(site, kind);
            room.area = have.isEmpty() ? key(kind, 1) : have.get(0);
        }
        AreaState s = state(site, room.area);
        if (room.areaNo <= 0) room.areaNo = s.nextRoomNo++;
        room.areaName = displayName(site, room.area);
    }

    /** The area of the room at (or within 4 blocks of) this spot, or null. */
    public static String areaAt(Site site, BlockPos pos) {
        for (Room r : site.rooms) if (r.area != null && r.contains(pos)) return r.area;
        for (Room r : site.rooms) if (r.area != null && r.box().inflate(4).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))) return r.area;
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

    // ---------------------------------------------------------------- clearance (D15)

    /** An area's clearance: the Director's setting, or the area's default. */
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

    /** Tells the door control which readers are locked down, and which hatch readers only work from below. No world access. */
    public static void registerGates(Site site) {
        for (BlockPos r : SiteManager.readers()) {
            String area = areaAt(site, r);
            if (area != null) DoorControl.setLocked(r, site.lockdown.contains(area));
        }
        for (BlockPos hatch : site.hatchReaders) DoorControl.setFromBelow(hatch);
    }

    // ---------------------------------------------------------------- lockdown

    /** Shuts every door in the area's rooms and stops their readers until the lockdown is lifted. */
    public static void lockdown(ServerLevel level, Site site, String key) {
        if (key == null || !site.lockdown.add(key)) return;
        registerGates(site);
        for (Room r : site.rooms) if (key.equals(r.area)) DoorControl.setDoorsInBox(level, r.box().inflate(1), false);
        dev.projectredacted.block.EmergencyBulkheadBlock.updateAll(level, site);
        String name = displayName(site, key);
        site.log("Lockdown: " + name + ".");
        if (!Cassie.announce(site, "Lockdown: " + name + ". All personnel remain in place.")) Cassie.alarm(site, "Lockdown: " + name);
    }

    public static void lift(ServerLevel level, Site site, String key) {
        if (!site.lockdown.remove(key)) return;
        registerGates(site);
        dev.projectredacted.block.EmergencyBulkheadBlock.updateAll(level, site);
        String name = displayName(site, key);
        site.log("Lockdown lifted: " + name + ".");
        if (!Cassie.announce(site, "Lockdown lifted: " + name + ".")) Cassie.broadcast(site, Component.literal("Lockdown lifted: " + name + ".").withStyle(ChatFormatting.GREEN), false);
    }

    private Areas() {}
}
