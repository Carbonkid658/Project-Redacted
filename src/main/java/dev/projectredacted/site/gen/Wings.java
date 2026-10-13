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
 * Layers, wings and sections (design §4A): the wing list (built in, plus data/&lt;ns&gt;/projectredacted/site_wings/),
 * which wing a room belongs to, where each wing grows, and the clearance of every area (D15).
 */
public final class Wings {
    public enum Layer {
        SURFACE, ADMINISTRATIVE, RESEARCH, CONTAINMENT, INFRASTRUCTURE;

        public static Layer parse(String s, Layer fallback) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    /** How wings join (§4A.4). */
    public enum Connection {
        HUB("Central hub", "A lobby with hallway arms; each wing's gate opens off an arm."),
        SPINE("Central spine", "One long main corridor; wings branch off both sides."),
        SECTORS("Sectors", "Each wing is its own block, joined by tunnels with a checkpoint at each end. Slow to cross, hard to raid.");

        public final String displayName, description;

        Connection(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    public static class Wing {
        public String letter;
        public String name;
        public Layer layer = Layer.ADMINISTRATIVE;
        public String color = "white";
        public int clearance = 1;
        public final List<String> roomTypes = new ArrayList<>();

        public String title() {
            return letter + " Wing";
        }
    }

    /** A wing's state in one site (saved with the site). */
    public static class WingState {
        /** Has its own gate and corridor; until then its rooms hang off the outpost hub. */
        public boolean founded;
        /** Director's override, or -1 for the default (the lowest of its sections). */
        public int clearance = -1;
        public List<Section> sections = new ArrayList<>();
        public int nextRoomNo = 1;

        public Section section(int number) {
            for (Section s : sections) if (s.number == number) return s;
            return null;
        }

        public int nextSection() {
            int n = 1;
            for (Section s : sections) n = Math.max(n, s.number + 1);
            return n;
        }
    }

    public static class Section {
        public int number;
        /** Director's override, or -1 for the default (the highest of its rooms). */
        public int clearance = -1;
        /** What kind of rooms it holds, e.g. cells with their observation rooms. */
        public String group;

        public Section() {}

        public Section(int number, String group) {
            this.number = number;
            this.group = group;
        }
    }

    /** Rooms per section (§4A.3). */
    public static final int SECTION_ROOMS = 6;
    public static final String ROLE_HUB = "hub", ROLE_LINK = "link", ROLE_SPINE = "spine", ROLE_TRANSITION = "transition", ROLE_TUNNEL = "tunnel",
            ROLE_GATE = "gate", ROLE_CORRIDOR = "corridor", ROLE_SECTION_GATE = "section_gate", ROLE_SECTION = "section", ROLE_ROOM = "room";

    private static final Map<String, Wing> WINGS = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    static {
        builtIns();
    }

    private static void builtIns() {
        WINGS.clear();
        add("A", "Entrance and Security", Layer.SURFACE, "red", 1, "entrance", "hallway_checkpoint", "transition_checkpoint", "armory", "prison",
                "security_office", "interrogation_room", "visitor_screening", "emergency_exit", "loading_dock");
        add("B", "Administration", Layer.ADMINISTRATIVE, "gold", 2, "directors_office", "admin_office", "conference_room", "records_room", "hr_office",
                "ethics_committee_office");
        add("C", "Personnel Services", Layer.ADMINISTRATIVE, "green", 1, "barracks", "single_bedroom", "cafeteria", "kitchen", "lounge", "bathroom",
                "medbay", "checkup_room", "waiting_room", "showers", "laundry");
        add("D", "Research and Development", Layer.RESEARCH, "blue", 2, "lab", "researcher_office", "testing_chamber", "sample_storage", "equipment_workshop");
        add("E", "Standard Containment", Layer.CONTAINMENT, "amber", 3, "light_cell", "medium_cell", "observation_room", "safe_storage_chamber", "sample_vault");
        add("F", "High-Security Containment", Layer.CONTAINMENT, "purple", 4, "heavy_cell", "security_station", "isolation_room");
        add("G", "Engineering and Infrastructure", Layer.INFRASTRUCTURE, "gray", 1, "generator", "water_pump", "circuit_breaker", "storage_room",
                "supply_depot", "ventilation", "server_room", "maintenance_workshop", "waste_management");
    }

    private static void add(String letter, String name, Layer layer, String color, int clearance, String... rooms) {
        Wing w = new Wing();
        w.letter = letter;
        w.name = name;
        w.layer = layer;
        w.color = color;
        w.clearance = clearance;
        for (String r : rooms) w.roomTypes.add(ProjectRedacted.MOD_ID + ":" + r);
        WINGS.put(letter, w);
    }

    public static Wing get(String letter) {
        return letter == null ? null : WINGS.get(letter.toUpperCase(Locale.ROOT));
    }

    public static Collection<Wing> all() {
        return WINGS.values();
    }

    /** Reloads datapack wings from data/&lt;ns&gt;/projectredacted/site_wings/*.json (same fields as {@link Wing}). */
    public static void reload(ResourceManager manager) {
        builtIns();
        Map<Identifier, Resource> found = manager.listResources("projectredacted/site_wings", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String file = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject o = GSON.fromJson(reader, JsonObject.class);
                String letter = (o.has("letter") ? o.get("letter").getAsString() : file).trim().toUpperCase(Locale.ROOT);
                if (letter.isEmpty()) continue;
                Wing w = WINGS.getOrDefault(letter, new Wing());
                w.letter = letter;
                if (o.has("name")) w.name = o.get("name").getAsString();
                if (w.name == null) w.name = letter + " Wing";
                if (o.has("layer")) w.layer = Layer.parse(o.get("layer").getAsString(), w.layer);
                if (o.has("color")) w.color = o.get("color").getAsString();
                if (o.has("clearance")) w.clearance = Math.max(0, Math.min(5, o.get("clearance").getAsInt()));
                if (o.has("room_types")) {
                    w.roomTypes.clear();
                    for (JsonElement t : o.getAsJsonArray("room_types")) {
                        String id = PartKit.normalize(t.getAsString());
                        if (id == null) continue;
                        for (Wing other : WINGS.values()) other.roomTypes.remove(id);
                        w.roomTypes.add(id);
                    }
                }
                WINGS.put(letter, w);
            } catch (Exception ex) {
                ProjectRedacted.LOG.warn("Couldn't load site wing {}: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- which wing

    /** The wing a room type belongs to: its own {@code wing} field, the wing list, then its category. */
    public static String wingOf(String typeId) {
        RoomType t = RoomTypes.get(typeId);
        if (t != null && t.wing != null && get(t.wing) != null) return t.wing.toUpperCase(Locale.ROOT);
        for (Wing w : WINGS.values()) if (w.roomTypes.contains(typeId)) return w.letter;
        String category = t == null ? "Custom" : t.category;
        return switch (category) {
            case "Core", "Offices" -> "B";
            case "Engineering" -> "G";
            case "Research" -> "D";
            case "Containment" -> "E";
            case "Checkpoint", "Security" -> "A";
            default -> "C";
        };
    }

    /** As {@link #wingOf(String)}, but an Observation Room goes where the cells it would watch are. */
    public static String wingFor(Site site, String typeId) {
        if ("projectredacted:observation_room".equals(typeId)) {
            int heavy = 0, watching = 0;
            for (Room r : site.rooms) {
                if (!"F".equals(r.wing)) continue;
                if (r.type.equals("projectredacted:heavy_cell")) heavy++;
                if (r.type.equals(typeId)) watching++;
            }
            if (heavy > watching) return "F";
        }
        return wingOf(typeId);
    }

    /** Rooms that share a section: cells with their observation rooms, beds with bathrooms (§4A.3). */
    public static String group(String typeId) {
        RoomType t = RoomTypes.get(typeId);
        if (t == null) return "Custom";
        String path = typeId.substring(typeId.indexOf(':') + 1);
        if (path.equals("observation_room") || t.isContainment()) return "cells";
        if (path.equals("bathroom") || path.equals("showers") || path.equals("laundry") || t.isHousing()) return "housing";
        if (path.equals("kitchen") || path.equals("cafeteria") || path.equals("lounge")) return "food";
        return t.category;
    }

    public static WingState state(Site site, String letter) {
        return site.wings.computeIfAbsent(letter, k -> new WingState());
    }

    public static Connection connection(Site site) {
        if (site.connection != null) return site.connection;
        return site.type == SiteType.UNDERGROUND ? Connection.SPINE : Connection.HUB;
    }

    // ---------------------------------------------------------------- where wings go (§4A.1)

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

    /** Directions of the wing slots, in degrees from the hub toward the Entrance (§4A.1, Surface-Flat rings). */
    private static double slotAngle(String letter) {
        return switch (letter) {
            case "A" -> 0;
            case "B" -> 50;
            case "C" -> -50;
            case "D" -> 100;
            case "G" -> -100;
            case "E" -> 155;
            case "F" -> 205;
            default -> 180 + 23 * (letter.charAt(0) - 'H');
        };
    }

    /** The middle of the area a wing grows into: its ring direction from the hub, on its layer's storey. */
    public static BlockPos slot(Site site, String letter, Box bounds, int baseFloor) {
        BlockPos hub = hubCenter(site);
        Room entrance = site.firstEntrance();
        double toward = 0;
        if (entrance != null) toward = Math.atan2(entrance.center().getZ() - hub.getZ(), entrance.center().getX() - hub.getX());
        double a = toward + Math.toRadians(slotAngle(letter));
        double radius = 0.6 * Math.min(bounds.x2() - bounds.x1(), bounds.z2() - bounds.z1()) / 2.0;
        Wing w = get(letter);
        Integer storey = w == null ? null : storey(site, w.layer);
        int y = baseFloor + SitePart.STOREY * (storey == null ? 0 : storey);
        return new BlockPos(hub.getX() + (int) Math.round(Math.cos(a) * radius), y, hub.getZ() + (int) Math.round(Math.sin(a) * radius));
    }

    private static BlockPos hubCenter(Site site) {
        for (PlacedPart p : site.parts) {
            if (p.removed || !ROLE_HUB.equals(p.role)) continue;
            Box b = p.box();
            if (b != null) return b.center();
        }
        return site.center();
    }

    // ---------------------------------------------------------------- rooms

    /** Gives a room its wing and number: the wing whose parts it sits among, or its type's wing (§4A.5). */
    public static void assign(Site site, Room room) {
        if (room.wing == null) {
            String inside = null;
            for (PlacedPart p : site.parts) {
                if (p.removed || p.wing == null) continue;
                Box b = p.box();
                if (b != null && b.grow(4).contains(room.center())) {
                    inside = p.wing;
                    break;
                }
            }
            room.wing = inside != null ? inside : wingFor(site, room.type);
        }
        if (room.wingNo <= 0) room.wingNo = state(site, room.wing).nextRoomNo++;
    }

    public static int rooms(Site site, String wing, int section) {
        int n = 0;
        for (Room r : site.rooms) if (wing.equals(r.wing) && r.section == section) n++;
        return n;
    }

    // ---------------------------------------------------------------- clearance (D15)

    public static int roomClearance(Room r) {
        RoomType t = r.typeDef();
        return t == null ? 0 : t.clearance;
    }

    /** A section's clearance: the Director's setting, or the highest of its rooms. */
    public static int sectionClearance(Site site, String wing, int section) {
        WingState ws = state(site, wing);
        Section s = ws.section(section);
        if (s != null && s.clearance >= 0) return s.clearance;
        int max = -1;
        for (Room r : site.rooms) if (wing.equals(r.wing) && r.section == section) max = Math.max(max, roomClearance(r));
        return max >= 0 ? max : defaultClearance(wing);
    }

    /** A wing's clearance: the Director's setting, or the lowest of its sections, so its corridor is open to anyone allowed in any part of it. */
    public static int wingClearance(Site site, String wing) {
        WingState ws = state(site, wing);
        if (ws.clearance >= 0) return ws.clearance;
        int min = Integer.MAX_VALUE;
        for (Section s : ws.sections) if (rooms(site, wing, s.number) > 0) min = Math.min(min, sectionClearance(site, wing, s.number));
        return min == Integer.MAX_VALUE ? defaultClearance(wing) : min;
    }

    public static int defaultClearance(String wing) {
        Wing w = get(wing);
        return w == null ? 1 : w.clearance;
    }

    /** The clearance a gate checks on its inner side and on its outer side. */
    private static int[] gateLevels(Site site, PlacedPart p) {
        if (p.wing == null) return new int[]{1, 1};
        if (ROLE_SECTION_GATE.equals(p.role)) return new int[]{sectionClearance(site, p.wing, p.section), wingClearance(site, p.wing)};
        return new int[]{wingClearance(site, p.wing), 1};
    }

    /**
     * Sets every gate reader to the clearance of the area it lets you into (D15) and remembers which way it
     * faces, so a reader only answers people standing in front of it. Run when a part or room finishes and when
     * the Director changes a clearance.
     */
    public static void applyReaders(ServerLevel level, Site site) {
        registerGates(site);
        for (PlacedPart p : site.parts) {
            if (p.removed || !p.built) continue;
            SitePart part = p.part();
            if (part == null || part.kind != SitePart.Kind.GATE) continue;
            labelSigns(level, p);
            int[] levels = gateLevels(site, p);
            for (BlockPos local : part.readers) {
                BlockPos w = PartBuilder.world(p, local);
                if (!level.isLoaded(w)) continue;
                BlockState s = level.getBlockState(w);
                if (!s.is(ModBlocks.KEYCARD_READER)) continue;
                int want = Math.max(1, Math.min(5, inward(part, p, local, s) ? levels[0] : levels[1]));
                if (s.getValue(KeycardReaderBlock.LEVEL) != want) level.setBlock(w, s.setValue(KeycardReaderBlock.LEVEL, want), 3);
                SiteManager.addReader(w);
            }
        }
    }

    /** Fills in the empty signs in a gate: "D WING" and its name, or "SECTION D-1". */
    private static void labelSigns(ServerLevel level, PlacedPart p) {
        Box b = p.box();
        if (b == null || p.wing == null) return;
        boolean section = ROLE_SECTION_GATE.equals(p.role);
        Wing w = get(p.wing);
        Component top = Component.literal(section ? "SECTION" : p.wing + " WING");
        Component bottom = Component.literal(section ? sectionLabel(p.wing, p.section) : w == null ? "" : w.name);
        for (BlockPos pos : BlockPos.betweenClosed(b.x1(), b.y1(), b.z1(), b.x2(), b.y2(), b.z2())) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            if (sign.getText(SignTextSlot.FRONT).hasMessage(false)) continue;
            sign.updateText(t -> t.asMutable().setLine(1, top).setLine(2, bottom).asImmutable(), SignTextSlot.FRONT);
        }
    }

    /** Whether passing this reader's door takes you away from the side the gate was entered from. */
    private static boolean inward(SitePart part, PlacedPart p, BlockPos local, BlockState state) {
        Direction facing = state.getValue(dev.projectredacted.block.FacingBlock.FACING);
        BlockPos w = PartBuilder.world(p, local);
        SitePart.Connector entry = part.worldConnector(Math.max(0, Math.min(part.connectors.size() - 1, p.entry)), p.origin, p.rot);
        BlockPos front = w.relative(facing, 2), back = w.relative(facing.getOpposite(), 2);
        return front.distSqr(entry.pos()) < back.distSqr(entry.pos());
    }

    /** Tells the door control which readers are one-sided and which are locked down. No world access. */
    public static void registerGates(Site site) {
        for (PlacedPart p : site.parts) {
            if (p.removed) continue;
            SitePart part = p.part();
            if (part == null || part.kind != SitePart.Kind.GATE) continue;
            boolean locked = p.wing != null && site.lockdown.contains(p.wing);
            for (BlockPos local : part.readers) {
                BlockPos w = PartBuilder.world(p, local);
                DoorControl.setOneSided(w, true);
                DoorControl.setLocked(w, locked);
            }
        }
        for (BlockPos hatch : site.hatchReaders) DoorControl.setFromBelow(hatch);
    }

    // ---------------------------------------------------------------- lockdown (§4A.3)

    /** Closes the wing's gate and section doors and stops their readers until the lockdown is lifted. */
    public static void lockdown(ServerLevel level, Site site, String wing) {
        if (wing == null || !site.lockdown.add(wing)) return;
        registerGates(site);
        for (PlacedPart p : site.parts) {
            if (p.removed || !wing.equals(p.wing)) continue;
            SitePart part = p.part();
            Box b = p.box();
            if (part == null || b == null || part.kind != SitePart.Kind.GATE) continue;
            DoorControl.setDoorsInBox(level, new AABB(b.x1(), b.y1(), b.z1(), b.x2() + 1, b.y2() + 1, b.z2() + 1), false);
        }
        Wing w = get(wing);
        String name = w == null ? wing + " Wing" : w.title();
        site.log("Lockdown: " + name + ".");
        if (!Cassie.announce(site, "Lockdown: " + name + ". All personnel remain in place.")) Cassie.alarm(site, "Lockdown: " + name);
    }

    public static void lift(ServerLevel level, Site site, String wing) {
        if (!site.lockdown.remove(wing)) return;
        registerGates(site);
        Wing w = get(wing);
        String name = w == null ? wing + " Wing" : w.title();
        site.log("Lockdown lifted: " + name + ".");
        if (!Cassie.announce(site, "Lockdown lifted: " + name + ".")) Cassie.broadcast(site, Component.literal("Lockdown lifted: " + name + ".").withStyle(ChatFormatting.GREEN), false);
    }

    // ---------------------------------------------------------------- labels

    /** "D Wing, section D-1". */
    public static String sectionLabel(String wing, int section) {
        return wing + "-" + section;
    }

    public static String title(String wing) {
        Wing w = get(wing);
        return w == null ? wing + " Wing" : w.title();
    }

    private Wings() {}
}
