package dev.projectredacted.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.room.Construction;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteType;
import dev.projectredacted.site.gen.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.List;

/**
 * /site parts (design §7.1), for operators building a kit: export the Part Frames in this world, test a kit by
 * generating every site type from it, and import a kit so this world uses it.
 */
public final class PartsCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("parts").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("test")
                        .executes(ctx -> test(ctx.getSource(), null, false))
                        .then(Commands.argument("kit", StringArgumentType.word())
                                .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "kit"), false))
                                .then(Commands.literal("build")
                                        .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "kit"), true)))));
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

    /** Generates every site type from the kit around the command's position, without building (unless asked). */
    private static int test(CommandSourceStack src, String kitName, boolean build) {
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        PartKit kit = kit(src, kitName);
        say(src, "Testing kit " + kit.name + " (" + kit.parts.size() + " own parts) at " + at.toShortString(), ChatFormatting.GOLD);
        for (String p : kit.problems) say(src, "  problem: " + p, ChatFormatting.RED);
        for (SitePart p : kit.parts) for (String problem : PartKit.check(p)) say(src, "  " + p.id + ": " + problem, ChatFormatting.RED);
        List<String> missing = kit.missingLevel0();
        if (!missing.isEmpty()) say(src, "  Placeholder parts used for: " + String.join(", ", missing), ChatFormatting.GRAY);
        int failures = 0;
        for (SiteType type : SiteType.values()) {
            Site site = fakeSite(level, at, type);
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
            for (RoomType t : RoomTypes.all()) {
                if (t.id.equals(RoomTypes.ENTRANCE) || t.id.equals(RoomTypes.DIRECTORS_OFFICE)) continue;
                tried++;
                SiteGen.Survey s = SiteGen.survey(level, site, 777L + tried);
                s.input().kit = kit;
                s.input().exitOk = (p, d) -> true;
                List<SitePart> sizes = kit.sizes(t.id);
                if (sizes.isEmpty()) continue;
                SitePlan room = Assembler.addRoom(s.input(), new Assembler.Goal(t.id, false, null, sizes.get(0)));
                if (!room.ok()) continue;
                SiteGen.commit(level, site, room, s, false);
                grown++;
            }
            for (PlacedPart p : site.parts) storeys = Math.max(storeys, Math.abs(p.storey) + 1);
            say(src, type.displayName + ": Level 0 ok (" + plan.parts.size() + " parts, " + plan.overwrites + " player blocks in the way, " + ms + " ms); grew "
                    + grown + " of " + tried + " room types, " + site.parts.size() + " parts over " + storeys + " storeys", grown == tried ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            if (build && type == SiteType.SURFACE_FLAT) buildNow(src, level, site);
        }
        say(src, failures == 0 ? "Kit test passed." : "Kit test: " + failures + " site type(s) failed.", failures == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        return failures == 0 ? 1 : 0;
    }

    private static Site fakeSite(ServerLevel level, BlockPos at, SiteType type) {
        Site site = new Site();
        site.id = "parts-test";
        site.anchor = at;
        site.mainframe = at;
        site.level = 3;
        site.type = type;
        return site;
    }

    /** Places every part of the test site right away, to see it in the world. */
    private static void buildNow(CommandSourceStack src, ServerLevel level, Site site) {
        long start = System.nanoTime();
        int blocks = 0;
        for (int i = 0; i < site.parts.size(); i++) {
            PartBuilder.Job job = PartBuilder.job(level, site, i);
            for (RoomBuilder.Placement p : job.placements) Construction.place(level, p);
            blocks += job.placements.size();
            PartBuilder.finish(level, site, i, job);
        }
        say(src, "Built the Surface-Flat test site: " + site.parts.size() + " parts, " + blocks + " blocks in "
                + (System.nanoTime() - start) / 1_000_000 + " ms.", ChatFormatting.GREEN);
    }

    private PartsCommand() {}
}
