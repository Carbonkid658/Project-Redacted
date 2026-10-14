package dev.projectredacted.site;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.raid.RaidManager;
import dev.projectredacted.room.Construction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owns every site in the world and ticks them. Sites live in the Overworld and are stored as JSON in
 * {@code <world>/projectredacted/sites.json}.
 */
public final class SiteManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(BlockPos.class, new BlockPosAdapter())
            .create();

    private static MinecraftServer server;
    private static Path file;
    private static Data data = new Data();
    private static int tickCounter;

    /** Ruined sites generate on worldgen threads, so they queue here first. */
    private static final Queue<RuinRecord> NEW_RUINS = new ConcurrentLinkedQueue<>();

    public static class Data {
        public Map<String, Site> sites = new LinkedHashMap<>();
        public Set<Long> readers = new HashSet<>();
        public List<RuinRecord> ruins = new ArrayList<>();
        public Map<UUID, BlockPos> libraryReturn = new HashMap<>();
        public boolean libraryBuilt;
    }

    /** A ruined facility found during world generation: its dead Mainframe and the rooms it had. */
    public static class RuinRecord {
        public BlockPos mainframe;
        public List<dev.projectredacted.room.Room> rooms = new ArrayList<>();
        public boolean claimed;
    }

    // ---- Lifecycle ----

    public static void load(MinecraftServer srv) {
        server = srv;
        file = srv.getWorldPath(LevelResource.ROOT).resolve(ProjectRedacted.MOD_ID).resolve("sites.json");
        data = new Data();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null) data = loaded;
                for (Site s : data.sites.values()) repair(s);
                ProjectRedacted.LOG.info("Loaded {} site(s)", data.sites.size());
            } catch (Exception e) {
                ProjectRedacted.LOG.error("Couldn't read {}; starting with no sites", file, e);
            }
        }
    }

    /** Fills in collections Gson leaves null when loading saves from older versions. */
    private static void repair(Site s) {
        if (s.rooms == null) s.rooms = new ArrayList<>();
        if (s.members == null) s.members = new LinkedHashMap<>();
        if (s.staff == null) s.staff = new LinkedHashMap<>();
        if (s.upgrades == null) s.upgrades = EnumSet.noneOf(Upgrade.class);
        s.upgrades.remove(null);
        if (s.pendingUpgrades == null) s.pendingUpgrades = new EnumMap<>(Upgrade.class);
        s.pendingUpgrades.remove(null);
        if (s.scpHomes == null) s.scpHomes = new HashMap<>();
        if (s.logLines == null) s.logLines = new ArrayList<>();
        if (s.type == null) s.type = SiteType.SURFACE_FLAT;
        if (s.parts == null) s.parts = new ArrayList<>();
        if (s.doorways == null) s.doorways = new ArrayList<>();
        s.doorways.removeIf(d -> d == null || d.pos == null || d.facing == null || d.type == null);
        if (s.forcedChunks == null) s.forcedChunks = new ArrayList<>();
        if (s.raiderEntries == null) s.raiderEntries = new ArrayList<>();
        if (s.staffPosts == null) s.staffPosts = new ArrayList<>();
        if (s.expandPriority == null) s.expandPriority = new ArrayList<>();
        for (var part : s.parts) {
            if (part.rooms == null) part.rooms = new ArrayList<>();
            if (part.reopen == null) part.reopen = new ArrayList<>();
        }
        // v4: existing sites keep their rooms; each room joins an area of its kind.
        if (s.areas == null) s.areas = new LinkedHashMap<>();
        s.areas.values().removeIf(java.util.Objects::isNull);
        for (var e : s.areas.entrySet()) {
            if (e.getValue().kind == null) e.getValue().kind = dev.projectredacted.site.gen.Areas.kindOf(e.getKey());
            if (e.getValue().number <= 0) e.getValue().number = dev.projectredacted.site.gen.Areas.numberOf(e.getKey());
        }
        if (s.lockdown == null) s.lockdown = new java.util.TreeSet<>();
        s.lockdown.removeIf(k -> !s.areas.containsKey(k));
        if (s.hatchReaders == null) s.hatchReaders = new ArrayList<>();
        s.rooms.removeIf(java.util.Objects::isNull);
        for (var r : s.rooms) if (r.type != null) dev.projectredacted.site.gen.Areas.assign(s, r);
        dev.projectredacted.site.gen.Areas.relabel(s);
        dev.projectredacted.site.gen.Areas.registerGates(s);
        s.staff.values().removeIf(r -> r.role == null);
        for (var r : s.staff.values()) if (r.assignment == null) r.assignment = new dev.projectredacted.staff.Assignment();
    }

    public static void save() {
        if (file == null) return;
        drainRuins();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("sites.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ProjectRedacted.LOG.error("Couldn't save sites", e);
        }
    }

    public static void unload() {
        server = null;
        file = null;
        data = new Data();
        dev.projectredacted.site.gen.PlanPreview.clearAll();
        dev.projectredacted.site.gen.AutoPlace.clear();
        DoorControl.clear();
    }

    public static MinecraftServer server() {
        return server;
    }

    public static ServerLevel overworld() {
        return server == null ? null : server.overworld();
    }

    public static void tick(MinecraftServer srv) {
        if (server == null) return;
        tickCounter++;
        drainRuins();
        ServerLevel level = srv.overworld();
        for (Site site : new ArrayList<>(data.sites.values())) {
            Construction.tick(level, site);
            if (tickCounter % 20 == 0) SiteLogic.tickSecond(level, site);
            if (tickCounter % 100 == 0) SiteLogic.tickStaff(level, site);
            if (tickCounter % 100 == 50) dev.projectredacted.site.gen.Expansion.restock(level, site);
            dev.projectredacted.site.gen.Expansion.tick(level, site);
            RaidManager.tick(level, site, tickCounter);
            Warhead.tick(level, site);
        }
        if (tickCounter % 10 == 0) DoorControl.tick(level, data.readers);
        if (tickCounter % 10 == 5) dev.projectredacted.site.gen.PlanPreview.tick(srv);
        if (tickCounter % 20 == 7) dev.projectredacted.site.gen.AutoPlace.tickHud(srv);
        if (tickCounter % 20 == 13) dev.projectredacted.room.KeyRooms.tickHud(srv);
        if (tickCounter % 10 == 3) dev.projectredacted.site.gen.PartFrames.tickOutlines(srv);
        if (tickCounter % 6000 == 0) save();
    }

    private static void drainRuins() {
        RuinRecord r;
        while ((r = NEW_RUINS.poll()) != null) data.ruins.add(r);
    }

    public static void queueRuin(RuinRecord record) {
        NEW_RUINS.add(record);
    }

    // ---- Lookups ----

    public static Collection<Site> sites() {
        return data.sites.values();
    }

    public static void add(Site site) {
        data.sites.put(site.id, site);
    }

    public static void remove(Site site) {
        data.sites.remove(site.id);
    }

    /** The site whose claim covers this position. Sites only exist in the Overworld. */
    public static Site at(Level level, BlockPos pos) {
        if (level.isClientSide() || level.dimension() != Level.OVERWORLD) return null;
        return at(pos);
    }

    public static Site at(BlockPos pos) {
        for (Site s : data.sites.values()) if (s.contains(pos)) return s;
        return null;
    }

    public static Site byMainframe(BlockPos pos) {
        for (Site s : data.sites.values()) if (s.mainframe.equals(pos)) return s;
        return null;
    }

    public static Site directedBy(UUID player) {
        for (Site s : data.sites.values()) if (player.equals(s.director)) return s;
        return null;
    }

    /** The site the player directs, or else the first site they're a member of. */
    public static Site memberSite(Player player) {
        Site own = directedBy(player.getUUID());
        if (own != null) return own;
        for (Site s : data.sites.values()) if (s.members.containsKey(player.getUUID())) return s;
        return null;
    }

    public static Site byId(String id) {
        return data.sites.get(id);
    }

    public static Site staffSite(UUID staffId) {
        for (Site s : data.sites.values()) if (s.staff.containsKey(staffId)) return s;
        return null;
    }

    public static void addReader(BlockPos pos) {
        data.readers.add(pos.asLong());
    }

    public static List<RuinRecord> ruins() {
        drainRuins();
        return data.ruins;
    }

    public static Map<UUID, BlockPos> libraryReturns() {
        return data.libraryReturn;
    }

    public static boolean libraryBuilt() {
        return data.libraryBuilt;
    }

    public static void markLibraryBuilt() {
        data.libraryBuilt = true;
    }

    private static class BlockPosAdapter implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
        @Override
        public JsonElement serialize(BlockPos pos, Type type, JsonSerializationContext ctx) {
            JsonArray a = new JsonArray();
            a.add(pos.getX());
            a.add(pos.getY());
            a.add(pos.getZ());
            return a;
        }

        @Override
        public BlockPos deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonArray a = json.getAsJsonArray();
            return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
    }

    private SiteManager() {}
}
