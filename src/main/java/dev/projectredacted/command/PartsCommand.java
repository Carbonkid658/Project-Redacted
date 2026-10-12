package dev.projectredacted.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.room.Construction;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteType;
import dev.projectredacted.site.gen.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * /site parts (design §7), for operators building a kit in a creative world: export every Part Frame to a kit
 * folder with a check report and preview sheet, test a kit by generating sites from it, and import a kit back
 * into a world with its frames for editing.
 */
public final class PartsCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("parts").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("export").then(Commands.argument("kit", StringArgumentType.word())
                        .executes(ctx -> export(ctx.getSource(), StringArgumentType.getString(ctx, "kit")))))
                .then(Commands.literal("import").then(Commands.argument("kit", StringArgumentType.word())
                        .executes(ctx -> importKit(ctx.getSource(), StringArgumentType.getString(ctx, "kit")))))
                .then(Commands.literal("test")
                        .executes(ctx -> test(ctx.getSource(), null, null))
                        .then(Commands.argument("kit", StringArgumentType.word())
                                .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "kit"), null))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (SiteType t : SiteType.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "kit"), StringArgumentType.getString(ctx, "type"))))));
    }

    public static Path kitsDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.GENERATED_DIR).resolve(ProjectRedacted.MOD_ID).resolve("kits");
    }

    private static PartKit kit(CommandSourceStack src, String name) {
        if (name == null || name.equals("default")) return PartKit.active();
        return PartKit.loadFolder(name, kitsDir(src.getLevel()).resolve(name));
    }

    private static void say(CommandSourceStack src, String text, ChatFormatting color) {
        src.sendSystemMessage(Component.literal(text).withStyle(color));
        ProjectRedacted.LOG.info("[parts] {}", text);
    }

    // ---------------------------------------------------------------- reading frames

    private static CompoundTag capture(Level level, PartFrames.Frame f) {
        BlockPos min = f.minPos(), max = f.maxPos();
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, min, new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1), false,
                List.of(Blocks.STRUCTURE_VOID));
        return t.save(new CompoundTag());
    }

    /** The part a frame holds right now, read from the world. */
    public static SitePart read(Level level, PartFrames.Frame f) {
        return PartKit.read(new PartKit("frame"), f.id, f.toJson(), capture(level, f));
    }

    // ---------------------------------------------------------------- export (§7.1 step 4)

    private static int export(CommandSourceStack src, String kitName) {
        String name = PartFrames.cleanId(kitName);
        if (name == null) return 0;
        ServerLevel level = src.getLevel();
        List<PartFrames.Frame> frames = PartFrames.all();
        if (frames.isEmpty()) {
            say(src, "No Part Frames in this world. Frame each part with the Part Frame tool first.", ChatFormatting.RED);
            return 0;
        }
        Path dir = kitsDir(level).resolve(name);
        PartKit kit = new PartKit(name);
        List<String> report = new ArrayList<>();
        report.add("Kit " + name + ": " + frames.size() + " part(s)");
        report.add("");
        try {
            Files.createDirectories(dir);
            for (PartFrames.Frame f : frames) {
                CompoundTag tag = capture(level, f);
                JsonObject json = f.toJson();
                NbtIo.writeCompressed(tag, dir.resolve(f.id + ".nbt"));
                try (Writer w = Files.newBufferedWriter(dir.resolve(f.id + ".json"))) {
                    GSON.toJson(json, w);
                }
                SitePart part = PartKit.read(kit, f.id, json, tag);
                kit.parts.add(part);
                List<String> problems = PartKit.check(part);
                report.add((problems.isEmpty() ? "OK   " : "FIX  ") + f.id + " (" + part.kind.label() + ", " + f.size() + ", "
                        + part.connectors.size() + " doorway(s), " + part.markers.size() + " marker(s))");
                for (String p : problems) report.add("       - " + p);
            }
            for (int i = 0; i < frames.size(); i++)
                for (int j = i + 1; j < frames.size(); j++)
                    if (frames.get(i).box().intersects(frames.get(j).box()))
                        report.add("FIX  frames " + frames.get(i).id + " and " + frames.get(j).id + " overlap; leave a gap of at least 1 block");
            List<String> missing = kit.missingLevel0();
            report.add("");
            report.add(missing.isEmpty() ? "Level 0 set complete." : "Missing for Level 0 (placeholders are used until added): " + String.join(", ", missing));
            Files.write(dir.resolve("report.txt"), report);
            preview(level, frames, kit, dir.resolve("preview.png"));
        } catch (Exception e) {
            ProjectRedacted.LOG.error("Part export failed", e);
            say(src, "Export failed: " + e.getMessage(), ChatFormatting.RED);
            return 0;
        }
        long fix = report.stream().filter(l -> l.startsWith("FIX")).count();
        say(src, "Exported " + frames.size() + " part(s) to " + dir + (fix > 0 ? " · " + fix + " to fix, see report.txt" : " · no problems found"),
                fix > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        return 1;
    }

    /** A sheet of every part seen from above, floor plans with the doorways coloured (§7.1). */
    private static void preview(ServerLevel level, List<PartFrames.Frame> frames, PartKit kit, Path out) {
        System.setProperty("java.awt.headless", "true");
        int scale = 4, gap = 12, label = 14, cols = Math.max(1, (int) Math.ceil(Math.sqrt(frames.size())));
        int cellW = 0, cellH = 0;
        for (PartFrames.Frame f : frames) {
            cellW = Math.max(cellW, (f.max[0] - f.min[0] + 1) * scale);
            cellH = Math.max(cellH, (f.max[2] - f.min[2] + 1) * scale);
        }
        int rows = (frames.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * (cellW + gap) + gap, rows * (cellH + gap + label) + gap, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 24, 28));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < frames.size(); i++) {
            PartFrames.Frame f = frames.get(i);
            int ox = gap + (i % cols) * (cellW + gap), oy = gap + (i / cols) * (cellH + gap + label) + label;
            for (int x = f.min[0]; x <= f.max[0]; x++)
                for (int z = f.min[2]; z <= f.max[2]; z++) {
                    int rgb = 0x202020;
                    // First thing above the floor, or the floor itself: a floor plan rather than the roof.
                    for (int y = Math.min(f.max[1], f.min[1] + 4); y >= f.min[1]; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState s = level.getBlockState(pos);
                        if (s.isAir()) continue;
                        rgb = s.getMapColor(level, pos).col;
                        if (y > f.min[1] + 1) rgb = darker(rgb);
                        break;
                    }
                    g.setColor(new Color(rgb));
                    g.fillRect(ox + (x - f.min[0]) * scale, oy + (z - f.min[2]) * scale, scale, scale);
                }
            SitePart part = i < kit.parts.size() ? kit.parts.get(i) : null;
            if (part != null) {
                for (SitePart.Connector c : part.connectors) {
                    g.setColor(switch (c.type()) {
                        case SitePart.DOOR -> new Color(255, 210, 60);
                        case SitePart.HALL -> new Color(80, 220, 90);
                        case SitePart.SURFACE_EXIT -> new Color(240, 70, 60);
                        default -> new Color(80, 160, 255);
                    });
                    g.fillRect(ox + c.pos().getX() * scale - 1, oy + c.pos().getZ() * scale - 1, scale + 2, scale + 2);
                }
            }
            try {
                g.setColor(Color.WHITE);
                g.drawString(f.id + " · " + f.kind, ox, oy - 3);
            } catch (Throwable ignored) {
                // No fonts on this server; the sheet still works without labels.
            }
        }
        g.dispose();
        try {
            javax.imageio.ImageIO.write(img, "png", out.toFile());
        } catch (Exception e) {
            ProjectRedacted.LOG.warn("Couldn't write {}", out, e);
        }
    }

    private static int darker(int rgb) {
        int r = (rgb >> 16 & 255) * 3 / 4, g = (rgb >> 8 & 255) * 3 / 4, b = (rgb & 255) * 3 / 4;
        return r << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------- import (§7.2)

    /** Places a kit's parts in a row starting where the command runs, each with its frame, for editing. */
    private static int importKit(CommandSourceStack src, String kitName) {
        ServerLevel level = src.getLevel();
        Path dir = kitsDir(level).resolve(kitName);
        if (!Files.isDirectory(dir)) {
            say(src, "No kit folder at " + dir, ChatFormatting.RED);
            return 0;
        }
        BlockPos at = BlockPos.containing(src.getPosition()).offset(3, 0, 3);
        int x = 0, placed = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path json : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String id = json.getFileName().toString().replace(".json", "");
                Path nbt = json.resolveSibling(id + ".nbt");
                if (!Files.exists(nbt)) continue;
                JsonObject o;
                try (Reader r = Files.newBufferedReader(json)) {
                    o = GSON.fromJson(r, JsonObject.class);
                }
                CompoundTag tag = NbtIo.readCompressed(nbt, NbtAccounter.unlimitedHeap());
                StructureTemplate t = new StructureTemplate();
                t.load(BuiltInRegistries.BLOCK, tag);
                Vec3i size = t.getSize();
                BlockPos origin = at.offset(x, 0, 0);
                t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
                PartFrames.Frame old = PartFrames.byId(id);
                if (old != null) PartFrames.remove(old);
                PartFrames.Frame f = PartFrames.add(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
                f.id = id;
                if (o.has("kind")) f.kind = o.get("kind").getAsString();
                if (o.has("room_type")) f.roomType = PartKit.normalize(o.get("room_type").getAsString());
                if (o.has("weight")) f.weight = o.get("weight").getAsInt();
                if (o.has("rooms")) {
                    for (var el : o.getAsJsonArray("rooms")) {
                        JsonObject r = el.getAsJsonObject();
                        PartFrames.FrameRoom fr = new PartFrames.FrameRoom();
                        fr.type = PartKit.normalize(r.get("type").getAsString());
                        fr.from = world(origin, r.getAsJsonArray("from"));
                        fr.to = world(origin, r.getAsJsonArray("to"));
                        if (r.has("door")) fr.door = r.get("door").getAsString();
                        f.rooms.add(fr);
                    }
                }
                x += size.getX() + 3;
                placed++;
            }
        } catch (Exception e) {
            ProjectRedacted.LOG.error("Part import failed", e);
            say(src, "Import failed: " + e.getMessage(), ChatFormatting.RED);
            return 0;
        }
        PartFrames.save();
        say(src, "Placed " + placed + " part(s) from kit " + kitName + " in a row going east, each with its frame.", ChatFormatting.GREEN);
        return placed > 0 ? 1 : 0;
    }

    private static int[] world(BlockPos origin, com.google.gson.JsonArray a) {
        return new int[]{origin.getX() + a.get(0).getAsInt(), origin.getY() + a.get(1).getAsInt(), origin.getZ() + a.get(2).getAsInt()};
    }

    // ---------------------------------------------------------------- test (§7.2)

    private static SiteType parseType(String s) {
        if (s == null) return null;
        String t = s.toUpperCase(Locale.ROOT).replace('-', '_');
        if (t.equals("FLAT")) t = "SURFACE_FLAT";
        if (t.equals("TALL")) t = "SURFACE_TALL";
        try {
            return SiteType.valueOf(t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Without a site type: plans every site type from the kit and grows each one by every room type, as a check.
     * With one: generates a full Level 0 site of that type in front of you (each run rerolls).
     */
    private static int test(CommandSourceStack src, String kitName, String typeName) {
        try {
            return runTest(src, kitName, typeName);
        } catch (RuntimeException e) {
            ProjectRedacted.LOG.error("[parts] test failed", e);
            say(src, "Test failed: " + e, ChatFormatting.RED);
            return 0;
        }
    }

    private static int runTest(CommandSourceStack src, String kitName, String typeName) {
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        PartKit kit = kit(src, kitName);
        if (typeName != null) {
            SiteType type = parseType(typeName);
            if (type == null) {
                say(src, "Unknown site type " + typeName + ". Use underground, surface_flat or surface_tall.", ChatFormatting.RED);
                return 0;
            }
            if (src.getEntity() != null) at = at.relative(src.getEntity().getDirection(), 24);
            Site site = fakeSite(at, type);
            SiteGen.Survey survey = SiteGen.survey(level, site, RANDOM.nextLong());
            survey.input().kit = kit;
            SitePlan plan = SiteGen.planLevel0(survey, site);
            if (!plan.ok()) {
                say(src, type.displayName + ": Level 0 failed: " + plan.error, ChatFormatting.RED);
                return 0;
            }
            SiteGen.commit(level, site, plan, survey, false);
            buildNow(src, level, site, type);
            return 1;
        }
        say(src, "Testing kit " + kit.name + " (" + kit.parts.size() + " own parts) at " + at.toShortString(), ChatFormatting.GOLD);
        for (String p : kit.problems) say(src, "  problem: " + p, ChatFormatting.RED);
        for (SitePart p : kit.parts) for (String problem : PartKit.check(p)) say(src, "  " + p.id + ": " + problem, ChatFormatting.RED);
        List<String> missing = kit.missingLevel0();
        if (!missing.isEmpty()) say(src, "  Placeholder parts used for: " + String.join(", ", missing), ChatFormatting.GRAY);
        int failures = 0;
        for (SiteType type : SiteType.values()) {
            Site site = fakeSite(at, type);
            long start = System.nanoTime();
            SiteGen.Survey survey = SiteGen.survey(level, site, 12345L);
            survey.input().kit = kit;
            SitePlan plan = SiteGen.planLevel0(survey, site);
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (!plan.ok()) {
                failures++;
                say(src, type.displayName + ": Level 0 FAILED: " + plan.error, ChatFormatting.RED);
                continue;
            }
            SiteGen.commit(level, site, plan, survey, false);
            site.generated = true;
            int grown = 0, tried = 0, storeys = 0;
            List<String> failed = new ArrayList<>();
            long growStart = System.nanoTime();
            for (RoomType t : RoomTypes.all()) {
                if (t.id.equals(RoomTypes.ENTRANCE) || t.id.equals(RoomTypes.DIRECTORS_OFFICE)) continue;
                tried++;
                SiteGen.Survey s = SiteGen.survey(level, site, 777L + tried);
                s.input().kit = kit;
                s.input().exitOk = (p, d) -> true;
                List<SitePart> sizes = kit.sizes(t.id);
                if (sizes.isEmpty()) continue;
                SitePlan room = SiteGen.addRoom(site, s.input(), t.id, sizes.get(0));
                if (!room.ok()) {
                    failed.add(t.name);
                    continue;
                }
                SiteGen.commit(level, site, room, s, false);
                grown++;
            }
            long growMs = (System.nanoTime() - growStart) / 1_000_000;
            for (PlacedPart p : site.parts) storeys = Math.max(storeys, Math.abs(p.storey) + 1);
            say(src, type.displayName + ": Level 0 ok (" + plan.parts.size() + " parts, " + plan.overwrites + " player blocks in the way, " + ms + " ms); grew "
                    + grown + " of " + tried + " room types in " + growMs + " ms, " + site.parts.size() + " parts over " + storeys + " storeys"
                    + (failed.isEmpty() ? "" : "; no space for " + String.join(", ", failed)), grown == tried ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        }
        say(src, failures == 0 ? "Kit test passed." : "Kit test: " + failures + " site type(s) failed.", failures == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        return failures == 0 ? 1 : 0;
    }

    private static Site fakeSite(BlockPos at, SiteType type) {
        Site site = new Site();
        site.id = "parts-test";
        site.anchor = at;
        site.mainframe = at;
        site.level = 3;
        site.type = type;
        return site;
    }

    /** Places every part of the test site right away. */
    private static void buildNow(CommandSourceStack src, ServerLevel level, Site site, SiteType type) {
        long start = System.nanoTime();
        int blocks = 0;
        for (int i = 0; i < site.parts.size(); i++) {
            PartBuilder.Job job = PartBuilder.job(level, site, i);
            for (RoomBuilder.Placement p : job.placements) Construction.place(level, p);
            blocks += job.placements.size();
            PartBuilder.finish(level, site, i, job, false);
        }
        say(src, "Built a " + type.displayName + " Level 0 test site at " + site.anchor.toShortString() + ": " + site.parts.size() + " parts, "
                + blocks + " blocks in " + (System.nanoTime() - start) / 1_000_000 + " ms.", ChatFormatting.GREEN);
    }

    private PartsCommand() {}
}
