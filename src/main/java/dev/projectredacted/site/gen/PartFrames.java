package dev.projectredacted.site.gen;

import com.google.gson.*;
import dev.projectredacted.ProjectRedacted;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Part Frames (design §7.1): boxes marked around hand-built parts in a creative world, with the settings the
 * export writes into each part file. Saved in the world folder.
 */
public final class PartFrames {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class FrameRoom {
        public String type;
        public int[] from, to;
        public String door = "north";
    }

    public static class Frame {
        public String id;
        public String kind = "room";
        public String roomType;
        public int weight = 10;
        public int[] min, max;
        public List<FrameRoom> rooms = new ArrayList<>();

        public BlockPos minPos() {
            return new BlockPos(min[0], min[1], min[2]);
        }

        public BlockPos maxPos() {
            return new BlockPos(max[0], max[1], max[2]);
        }

        public Box box() {
            return new Box(min[0], min[1], min[2], max[0], max[1], max[2]);
        }

        public String size() {
            return (max[0] - min[0] + 1) + "×" + (max[2] - min[2] + 1) + "×" + (max[1] - min[1] + 1);
        }

        /** The part file (§7.1): kind, room type, weight and rooms in the part's own coordinates. */
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("kind", kind);
            if (roomType != null) o.addProperty("room_type", roomType);
            o.addProperty("weight", weight);
            if (!rooms.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (FrameRoom r : rooms) {
                    JsonObject jr = new JsonObject();
                    jr.addProperty("type", r.type);
                    jr.add("from", local(r.from));
                    jr.add("to", local(r.to));
                    jr.addProperty("door", r.door);
                    arr.add(jr);
                }
                o.add("rooms", arr);
            }
            return o;
        }

        private JsonArray local(int[] p) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < 3; i++) a.add(p[i] - min[i]);
            return a;
        }
    }

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static Path file;

    public static List<Frame> all() {
        return FRAMES;
    }

    public static Frame byId(String id) {
        for (Frame f : FRAMES) if (f.id.equals(id)) return f;
        return null;
    }

    public static Frame at(BlockPos pos) {
        for (Frame f : FRAMES) if (f.box().contains(pos)) return f;
        return null;
    }

    public static Frame add(BlockPos a, BlockPos b) {
        Frame f = new Frame();
        Box box = Box.of(a, b);
        f.min = new int[]{box.x1(), box.y1(), box.z1()};
        f.max = new int[]{box.x2(), box.y2(), box.z2()};
        int n = FRAMES.size() + 1;
        while (byId("part_" + n) != null) n++;
        f.id = "part_" + n;
        FRAMES.add(f);
        save();
        return f;
    }

    public static void remove(Frame f) {
        FRAMES.remove(f);
        save();
    }

    /** Lower case letters, digits and underscores, so the name works as a file name. */
    public static String cleanId(String s) {
        String id = s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return id.isEmpty() ? null : id.length() > 40 ? id.substring(0, 40) : id;
    }

    public static void load(MinecraftServer server) {
        FRAMES.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve(ProjectRedacted.MOD_ID).resolve("part_frames.json");
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Frame[] list = GSON.fromJson(r, Frame[].class);
            if (list != null) {
                for (Frame f : list) {
                    if (f == null || f.id == null || f.min == null || f.max == null) continue;
                    if (f.rooms == null) f.rooms = new ArrayList<>();
                    FRAMES.add(f);
                }
            }
        } catch (Exception e) {
            ProjectRedacted.LOG.error("Couldn't read {}", file, e);
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(FRAMES, w);
            }
        } catch (Exception e) {
            ProjectRedacted.LOG.error("Couldn't save part frames", e);
        }
    }

    public static void unload() {
        FRAMES.clear();
        file = null;
    }

    /** Runs every 10 ticks: frames show as outlines while the Part Frame is held. */
    public static void tickOutlines(MinecraftServer server) {
        if (FRAMES.isEmpty() && server.getPlayerList().getPlayers().isEmpty()) return;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getMainHandItem().is(dev.projectredacted.registry.ModItems.PART_FRAME)) continue;
            var level = (net.minecraft.server.level.ServerLevel) p.level();
            BlockPos first = dev.projectredacted.item.PartFrameItem.firstCorner(p);
            if (first != null) level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, first.getX() + 0.5, first.getY() + 1.1, first.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0);
            for (Frame f : FRAMES) {
                Box b = f.box();
                if (b.center().distSqr(p.blockPosition()) > 64 * 64) continue;
                int step = Math.max(1, (b.width() + b.length()) / 16);
                for (int x = b.x1(); x <= b.x2() + 1; x += step) for (int[] yz : new int[][]{{b.y1(), b.z1()}, {b.y1(), b.z2() + 1}, {b.y2() + 1, b.z1()}, {b.y2() + 1, b.z2() + 1}})
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, yz[0], yz[1], 1, 0, 0, 0, 0);
                for (int z = b.z1(); z <= b.z2() + 1; z += step) for (int[] xy : new int[][]{{b.x1(), b.y1()}, {b.x2() + 1, b.y1()}, {b.x1(), b.y2() + 1}, {b.x2() + 1, b.y2() + 1}})
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, xy[0], xy[1], z, 1, 0, 0, 0, 0);
                for (int y = b.y1(); y <= b.y2() + 1; y += step) for (int[] xz : new int[][]{{b.x1(), b.z1()}, {b.x2() + 1, b.z1()}, {b.x1(), b.z2() + 1}, {b.x2() + 1, b.z2() + 1}})
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, xz[0], y, xz[1], 1, 0, 0, 0, 0);
            }
        }
    }

    private PartFrames() {}
}
