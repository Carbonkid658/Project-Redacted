package dev.projectredacted.room;

import com.google.gson.Gson;
import dev.projectredacted.ProjectRedacted;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.*;

/** The room catalog: built-in rooms plus any added by datapacks. */
public final class RoomTypes {
    public static final List<String> CATEGORIES = List.of("Core", "Offices", "Engineering", "Medical", "Common", "Research",
            "Containment", "Checkpoint", "Security", "Housing", "Custom");

    private static final Map<String, RoomType> TYPES = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    static {
        builtIns();
    }

    public static final String ENTRANCE = "projectredacted:entrance";
    public static final String DIRECTORS_OFFICE = "projectredacted:directors_office";
    public static final String BARRACKS = "projectredacted:barracks";
    public static final String EMERGENCY_EXIT = "projectredacted:emergency_exit";

    private static void builtIns() {
        TYPES.clear();
        add("entrance", "Entrance", "Core", "entrance", 7, 24, 4, 8, 0, 0, null, 0, 0, "minecraft:dark_oak_door",
                "Where you enter and exit. Raiders break in here and push deeper.", "minecraft:stone_bricks", 32);
        add("directors_office", "Director's Office", "Core", "directors_office", 9, 20, 4, 8, 0, 0, null, 0, 4, "projectredacted:site_director_computer",
                "Holds the Mainframe and Terminal. The site's management layer.", "minecraft:bookshelf", 4, "projectredacted:plastic_block", 8);
        add("admin_office", "Administration Office", "Offices", "office", 7, 24, 4, 8, 2, 0, "ADMINISTRATION", 1, 2, "minecraft:writable_book",
                "Lowers upgrade cost and time. Full of cubicles.", "projectredacted:plastic_block", 12, "minecraft:paper", 32);
        add("researcher_office", "Researcher Office", "Offices", "office", 7, 24, 4, 8, 1, 0, "RESEARCHER", 0, 2, "minecraft:lectern",
                "Somewhere for researchers to work besides labs and observation rooms.", "projectredacted:plastic_block", 8, "minecraft:book", 4);
        add("water_pump", "Water Pump", "Engineering", "water_pump", 5, 16, 4, 8, 2, 0, null, 0, 1, "minecraft:water_bucket",
                "Pumps water through the site for Bathrooms, the Cafeteria and the Kitchen.", "minecraft:copper_block", 6, "minecraft:bucket", 2);
        add("generator", "Generator", "Engineering", "generator", 5, 16, 4, 8, 0, 0, null, 0, 1, "projectredacted:generator_core",
                "Generates power for the site.", "minecraft:iron_block", 2, "minecraft:redstone_block", 2, "minecraft:coal_block", 4);
        add("circuit_breaker", "Circuit Breaker", "Engineering", "circuit_breaker", 3, 12, 4, 6, 0, 0, null, 0, 1, "projectredacted:circuit_breaker",
                "A nook with a breaker. Lets more rooms connect to power.", "minecraft:iron_ingot", 8, "minecraft:redstone", 16);
        add("storage_room", "Storage Room", "Engineering", "storage", 5, 16, 4, 8, 1, 0, "ENGINEER", 0, 1, "minecraft:barrel",
                "Engineer storage. Lowers the cost of engineering upgrades.", "minecraft:chest", 4, "minecraft:iron_ingot", 4);
        add("supply_depot", "Supply Depot", "Engineering", "storage", 9, 24, 4, 8, 1, 0, null, 2, 1, "minecraft:chest",
                "One shared pool of chests and barrels. Auto-placed rooms and auto-expand pay from it first; engineers assigned here restock it from Storage Rooms. One per site.",
                "minecraft:chest", 8, "minecraft:barrel", 8, "minecraft:iron_ingot", 8);
        TYPES.get("projectredacted:supply_depot").unique = true;
        add("medbay", "Medbay", "Medical", "medbay", 7, 24, 4, 8, 2, 1, "MEDICAL", 0, 1, "minecraft:golden_apple",
                "Hospital-style ward where staff are treated.", "minecraft:white_wool", 6, "projectredacted:plastic_block", 8);
        add("checkup_room", "Checkup Room", "Medical", "checkup", 4, 10, 4, 6, 1, 0, "MEDICAL", 0, 1, "minecraft:brewing_stand",
                "A single room a doctor can use.", "minecraft:white_wool", 3, "projectredacted:plastic_block", 4);
        add("waiting_room", "Waiting Room", "Medical", "waiting", 5, 16, 4, 6, 1, 0, "MEDICAL", 0, 0, "minecraft:oak_stairs",
                "Injured staff queue here instead of working hurt.", "minecraft:oak_planks", 16);
        add("cafeteria", "Cafeteria", "Common", "cafeteria", 7, 24, 4, 8, 1, 2, null, 0, 0, "minecraft:bread",
                "Where staff get food. Needs a Kitchen and water.", "minecraft:oak_planks", 24, "minecraft:smoker", 1);
        add("kitchen", "Kitchen", "Common", "kitchen", 5, 16, 4, 6, 2, 2, null, 0, 0, "minecraft:smoker",
                "Supplies food to the Cafeteria.", "minecraft:smoker", 2, "minecraft:cauldron", 1, "minecraft:barrel", 2);
        add("lounge", "Lounge", "Common", "lounge", 5, 20, 4, 8, 1, 0, null, 0, 0, "minecraft:jukebox",
                "Entertainment. Raises morale.", "minecraft:jukebox", 1, "minecraft:white_carpet", 8);
        add("bathroom", "Bathroom", "Common", "bathroom", 4, 16, 4, 6, 1, 2, null, 0, 0, "minecraft:cauldron",
                "Improves staff morale. Needs water.", "minecraft:cauldron", 2, "minecraft:hopper", 2);
        add("lab", "Lab", "Research", "lab", 6, 24, 4, 8, 2, 1, "RESEARCHER", 0, 2, "minecraft:brewing_stand",
                "Hands-on research. Completed research leaves Research Documents in the lab's barrel.", "minecraft:brewing_stand", 2, "minecraft:glass", 8, "projectredacted:plastic_block", 8);
        add("observation_room", "Observation Room", "Research", "observation", 4, 12, 4, 6, 1, 0, "RESEARCHER", 0, 2, "minecraft:spyglass",
                "Build it next to a containment cell so researchers can do their tasks.", "projectredacted:reinforced_glass", 4, "minecraft:lectern", 1);
        add("light_cell", "Light Cell", "Containment", "light_cell", 4, 16, 4, 8, 0, 0, null, 0, 2, "minecraft:smooth_stone",
                "A door and basic blocks.", "minecraft:smooth_stone", 32, "minecraft:iron_door", 1);
        add("medium_cell", "Medium Cell", "Containment", "medium_cell", 5, 20, 4, 10, 1, 0, null, 0, 3, "minecraft:tuff_bricks",
                "Tuff walls, reinforced door and glass.", "minecraft:tuff_bricks", 48, "projectredacted:reinforced_door", 1, "projectredacted:reinforced_glass", 4);
        add("heavy_cell", "Heavy Cell", "Containment", "heavy_cell", 5, 24, 5, 12, 2, 0, null, 1, 4, "projectredacted:containment_concrete",
                "Blast-resistant cell for dangerous SCPs.", "projectredacted:containment_concrete", 64, "projectredacted:reinforced_door", 1);
        add("hallway_checkpoint", "Hallway Checkpoint", "Checkpoint", "hallway_checkpoint", 3, 12, 4, 6, 1, 0, null, 0, 1, "projectredacted:keycard_reader",
                "A secured door in a hallway.", "minecraft:iron_door", 2, "projectredacted:keycard_reader", 2);
        add("transition_checkpoint", "Transition Checkpoint", "Checkpoint", "transition_checkpoint", 5, 16, 4, 8, 1, 0, "SECURITY", 0, 2, "minecraft:iron_door",
                "A guarded room joining two parts of the site.", "minecraft:iron_door", 2, "projectredacted:keycard_reader", 2, "projectredacted:reinforced_glass", 4);
        add("armory", "Armory", "Security", "armory", 5, 16, 4, 8, 1, 0, "SECURITY", 0, 3, "minecraft:iron_chestplate",
                "Holds weapons and armor. Boosts damage and armor of nearby security.", "minecraft:iron_block", 2, "minecraft:anvil", 1, "minecraft:smithing_table", 1);
        add("prison", "Prison", "Security", "prison", 7, 24, 4, 8, 1, 0, "SECURITY", 1, 3, "minecraft:iron_bars",
                "Holds D-Class personnel. A powered prison takes in a new D-Class each day while it has room.", "minecraft:iron_bars", 32, "minecraft:white_bed", 2);
        add("barracks", "Barracks", "Housing", "barracks", 7, 20, 4, 6, 0, 0, null, 0, 0, "minecraft:red_bed",
                "Several beds in one room. Every bed is room for one more staff member.", "minecraft:red_bed", 4, "minecraft:oak_planks", 16);
        add("single_bedroom", "Single Bedroom", "Housing", "single_bedroom", 4, 10, 4, 6, 0, 0, null, 0, 0, "minecraft:blue_bed",
                "One bed. Opens onto a hall of similar rooms.", "minecraft:blue_bed", 1, "minecraft:oak_planks", 8);
        // v3 template rooms (§4A.2). Mostly cosmetic for now: they count for their wing; effects come later.
        // A wing: Entrance and Security
        add("security_office", "Security Office", "Security", "office", 5, 16, 4, 8, 2, 0, null, 0, 2, "minecraft:observer",
                "Surveillance post for A Wing. Cosmetic for now.", "projectredacted:plastic_block", 8, "minecraft:observer", 2);
        add("interrogation_room", "Interrogation Room", "Security", "waiting", 4, 10, 4, 6, 1, 0, null, 0, 2, "minecraft:iron_bars",
                "A table, two chairs and a lamp. Cosmetic for now.", "minecraft:smooth_stone", 16, "minecraft:lantern", 1);
        add("visitor_screening", "Visitor Screening", "Security", "waiting", 5, 16, 4, 6, 1, 0, null, 0, 1, "minecraft:spyglass",
                "Where visitors wait to be cleared. Cosmetic for now.", "minecraft:oak_planks", 12, "minecraft:glass", 4);
        add("emergency_exit", "Emergency Exit", "Security", "plain", 5, 9, 4, 6, 0, 0, null, 0, 1, "minecraft:iron_trapdoor",
                "A ladder shaft up to a surface hatch, locked from outside by a reader at A Wing clearance. Staff can get out from inside. You choose where it goes; auto-expand never builds one.",
                "minecraft:ladder", 16, "minecraft:iron_trapdoor", 1, "projectredacted:keycard_reader", 2);
        add("loading_dock", "Loading Dock", "Security", "storage", 7, 24, 4, 8, 0, 0, null, 0, 0, "minecraft:minecart",
                "Crates and a wide door. Decoration for now (D13).", "minecraft:barrel", 6, "minecraft:oak_planks", 16);
        // B wing: Administration
        add("conference_room", "Conference Room", "Offices", "office", 7, 20, 4, 8, 1, 0, null, 0, 2, "minecraft:oak_sign",
                "A long table for staff meetings. Cosmetic for now.", "minecraft:oak_planks", 16, "projectredacted:plastic_block", 4);
        add("records_room", "Records Room", "Offices", "storage", 5, 16, 4, 8, 0, 0, null, 0, 2, "minecraft:bookshelf",
                "Filing cabinets and archives. Cosmetic for now.", "minecraft:bookshelf", 4, "minecraft:barrel", 2);
        add("hr_office", "HR Office", "Offices", "office", 5, 16, 4, 8, 1, 0, null, 0, 1, "minecraft:paper",
                "Personnel files. Cosmetic for now.", "projectredacted:plastic_block", 6, "minecraft:paper", 16);
        add("ethics_committee_office", "Ethics Committee Office", "Offices", "office", 5, 16, 4, 8, 1, 0, null, 1, 3, "minecraft:writable_book",
                "Where the Ethics Committee reviews your procedures. Cosmetic for now.", "projectredacted:plastic_block", 8, "minecraft:book", 4);
        // C wing: Personnel Services
        add("showers", "Showers and Changing Room", "Common", "bathroom", 4, 16, 4, 6, 1, 1, null, 0, 0, "minecraft:water_bucket",
                "Showers and lockers. Cosmetic for now.", "minecraft:cauldron", 2, "minecraft:iron_ingot", 4);
        add("laundry", "Laundry", "Common", "storage", 4, 12, 4, 6, 1, 1, null, 0, 0, "minecraft:white_wool",
                "Washing for uniforms and bedding. Cosmetic for now.", "minecraft:cauldron", 2, "minecraft:white_wool", 8);
        // D wing: Research and Development
        add("testing_chamber", "Testing Chamber", "Research", "lab", 7, 24, 4, 10, 2, 0, null, 1, 3, "minecraft:target",
                "A sealed room for controlled tests. Cosmetic for now.", "projectredacted:reinforced_glass", 8, "minecraft:smooth_stone", 24);
        add("sample_storage", "Sample Storage", "Research", "storage", 4, 12, 4, 6, 1, 0, null, 0, 2, "minecraft:glass_bottle",
                "Shelves of labelled samples. Cosmetic for now.", "minecraft:barrel", 4, "minecraft:glass_bottle", 8);
        add("equipment_workshop", "Equipment Workshop", "Research", "storage", 5, 16, 4, 8, 1, 0, null, 0, 2, "minecraft:smithing_table",
                "Where research equipment is built and repaired. Cosmetic for now.", "minecraft:smithing_table", 1, "minecraft:iron_ingot", 8);
        // E wing: Standard Containment (not cells, so nothing is contained in them)
        add("safe_storage_chamber", "Safe Storage Chamber", "Research", "storage", 4, 12, 4, 6, 1, 0, null, 0, 3, "minecraft:iron_block",
                "Lockers for Safe-class items. Cosmetic for now.", "minecraft:iron_block", 2, "minecraft:barrel", 4);
        add("sample_vault", "Sample Vault", "Research", "storage", 4, 12, 4, 6, 1, 0, null, 1, 3, "minecraft:ender_chest",
                "A vault for anomalous samples. Cosmetic for now.", "projectredacted:containment_concrete", 16, "minecraft:barrel", 2);
        // F wing: High-Security Containment
        add("security_station", "Security Station", "Security", "office", 5, 12, 4, 6, 1, 0, null, 1, 4, "minecraft:shield",
                "Guards watching the heavy cells. Cosmetic for now.", "minecraft:iron_block", 2, "projectredacted:reinforced_glass", 4);
        add("isolation_room", "Isolation Room", "Security", "medium_cell", 5, 12, 4, 8, 1, 0, null, 1, 4, "minecraft:tuff_bricks",
                "A padded room for quarantine. Cosmetic for now.", "minecraft:tuff_bricks", 32, "projectredacted:reinforced_door", 1);
        // G wing: Engineering and Infrastructure
        add("ventilation", "Ventilation", "Engineering", "plain", 4, 16, 4, 8, 1, 0, null, 0, 1, "minecraft:iron_bars",
                "Fans and ducts. Cosmetic for now.", "minecraft:iron_bars", 16, "minecraft:iron_ingot", 4);
        add("server_room", "Server Room", "Engineering", "office", 5, 16, 4, 8, 2, 0, null, 1, 2, "minecraft:comparator",
                "Racks of servers. Cosmetic for now.", "minecraft:iron_block", 2, "minecraft:redstone", 16);
        add("maintenance_workshop", "Maintenance Workshop", "Engineering", "storage", 5, 16, 4, 8, 1, 0, null, 0, 1, "minecraft:anvil",
                "Tools for the engineers. Cosmetic for now.", "minecraft:anvil", 1, "minecraft:crafting_table", 2);
        add("waste_management", "Waste Management", "Engineering", "plain", 5, 16, 4, 8, 1, 1, null, 0, 1, "minecraft:composter",
                "Where the site's waste goes. Cosmetic for now.", "minecraft:composter", 2, "minecraft:hopper", 2);
    }

    private static void add(String path, String name, String category, String style, int minSide, int maxSide, int minHeight, int maxHeight,
                            int power, int water, String role, int level, int clearance, String icon, String description, Object... cost) {
        RoomType t = new RoomType();
        t.id = ProjectRedacted.MOD_ID + ":" + path;
        t.name = name;
        t.category = category;
        t.style = style;
        t.minSide = minSide;
        t.maxSide = maxSide;
        t.minHeight = minHeight;
        t.maxHeight = maxHeight;
        t.powerDraw = power;
        t.waterDraw = water;
        t.requiredRole = role;
        t.requiredLevel = level;
        t.clearance = clearance;
        t.icon = icon;
        t.description = description;
        for (int i = 0; i + 1 < cost.length; i += 2) t.cost.put((String) cost[i], (Integer) cost[i + 1]);
        t.builtIn = true;
        TYPES.put(t.id, t);
    }

    public static RoomType get(String id) {
        return TYPES.get(id);
    }

    public static Collection<RoomType> all() {
        return TYPES.values();
    }

    public static List<RoomType> inCategory(String category) {
        List<RoomType> list = new ArrayList<>();
        for (RoomType t : TYPES.values()) if (t.category.equals(category)) list.add(t);
        return list;
    }

    /** Reloads datapack rooms from data/<ns>/projectredacted/rooms/*.json. */
    public static void reload(ResourceManager manager) {
        builtIns();
        Map<Identifier, Resource> found = manager.listResources("projectredacted/rooms", id -> id.getPath().endsWith(".json"));
        int loaded = 0;
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (Reader reader = e.getValue().openAsReader()) {
                RoomType t = GSON.fromJson(reader, RoomType.class);
                if (t == null) continue;
                t.id = e.getKey().getNamespace() + ":" + name;
                if (t.name == null) t.name = name;
                if (!CATEGORIES.contains(t.category)) t.category = "Custom";
                if (t.cost == null) t.cost = new LinkedHashMap<>();
                TYPES.put(t.id, t);
                loaded++;
            } catch (Exception ex) {
                ProjectRedacted.LOG.warn("Couldn't load room type {}: {}", e.getKey(), ex.getMessage());
            }
        }
        if (loaded > 0) ProjectRedacted.LOG.info("Loaded {} custom room types from datapacks", loaded);
    }

    private RoomTypes() {}
}
