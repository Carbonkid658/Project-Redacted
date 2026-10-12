package dev.projectredacted.room;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.site.Cassie;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import dev.projectredacted.site.gen.Box;
import dev.projectredacted.site.gen.PartBuilder;
import dev.projectredacted.site.gen.PlacedPart;
import dev.projectredacted.site.gen.SiteGen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Automatic construction: designated rooms build themselves a few blocks per tick. Raids don't stop it,
 * but a finished room stays dormant until it's added to the Circuit Breaker.
 */
public final class Construction {
    public static final int BLOCKS_PER_TICK = 8;
    private static final Map<String, RoomBuilder.Plan> PLANS = new HashMap<>();
    private static final Map<String, PartBuilder.Job> JOBS = new HashMap<>();

    public static void tick(ServerLevel level, Site site) {
        tickParts(level, site);
        if (!site.mainframeOnline()) return;
        for (Room room : site.rooms) {
            if (room.built || room.freeform || room.part >= 0) continue;
            if (!level.isLoaded(room.center())) continue;
            RoomType type = room.typeDef();
            if (type == null) continue;
            RoomBuilder.Plan plan = PLANS.computeIfAbsent(key(site, room), k -> RoomBuilder.plan(room, type));
            int end = Math.min(plan.placements.size(), room.buildProgress + BLOCKS_PER_TICK);
            for (int i = room.buildProgress; i < end; i++) place(level, plan.placements.get(i));
            room.buildProgress = end;
            if (end > 0 && level.getGameTime() % 10 == 0) {
                BlockPos p = plan.placements.get(end - 1).pos();
                level.sendParticles(ParticleTypes.WAX_ON, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
            if (end >= plan.placements.size()) finish(level, site, room, type, plan);
            return; // one room at a time
        }
    }

    /**
     * Site parts (site generation): Level 0 builds fast with its chunks kept loaded; parts added later build one
     * at a time at the normal speed while the Mainframe is online.
     */
    private static void tickParts(ServerLevel level, Site site) {
        if (site.parts.isEmpty()) return;
        int budget = SiteGen.FAST_BLOCKS_PER_TICK;
        boolean fastLeft = false;
        for (int i = 0; i < site.parts.size(); i++) {
            PlacedPart pp = site.parts.get(i);
            if (pp.built || pp.removed) continue;
            if (pp.fast) fastLeft = true;
            if (!pp.fast && !site.mainframeOnline()) return;
            Box box = pp.box();
            if (box == null) {
                ProjectRedacted.LOG.warn("Site {} has an unknown part {}/{}; skipping it", site.id, pp.kit, pp.id);
                pp.built = true;
                continue;
            }
            BlockPos center = box.center();
            if (!level.isLoaded(center)) return;
            String key = site.id + "#p" + i;
            PartBuilder.Job job = JOBS.get(key);
            if (job == null) {
                job = PartBuilder.job(level, site, i);
                JOBS.put(key, job);
                pp.progress = 0; // terrain work depends on the world as it is now, so start the list over
            }
            int speed = pp.fast ? budget : BLOCKS_PER_TICK;
            int end = Math.min(job.placements.size(), pp.progress + speed);
            for (int k = pp.progress; k < end; k++) {
                RoomBuilder.Placement p = job.placements.get(k);
                if (pp.fast) PartBuilder.dropContents(level, p.pos());
                place(level, p);
            }
            int placed = end - pp.progress;
            pp.progress = end;
            if (!pp.fast && end > 0 && level.getGameTime() % 10 == 0) {
                BlockPos p = job.placements.get(end - 1).pos();
                level.sendParticles(ParticleTypes.WAX_ON, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
            if (end >= job.placements.size()) {
                PartBuilder.finish(level, site, i, job);
                JOBS.remove(key);
                if (!pp.fast) announce(site, pp);
            }
            if (!pp.fast) return; // one part at a time
            budget -= Math.max(1, placed);
            if (budget <= 0) return;
        }
        if (!fastLeft && !site.forcedChunks.isEmpty()) {
            SiteGen.releaseChunks(level, site);
            site.log("Level 0 facility construction complete.");
            String msg = "Level 0 facility construction complete.";
            if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        }
    }

    private static void announce(Site site, PlacedPart pp) {
        List<Room> rooms = SiteGen.rooms(site, pp);
        if (rooms.isEmpty()) return;
        Room room = rooms.get(0);
        RoomType type = room.typeDef();
        String msg = "Construction of " + room.displayName() + " complete."
                + (type != null && type.powerDraw > 0 && !room.connected ? " Add it to the Circuit Breaker to power it." : "");
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    public static void place(ServerLevel level, RoomBuilder.Placement p) {
        BlockPos pos = p.pos();
        if (level.getBlockState(pos).is(Blocks.BEDROCK)) return;
        if (p.state().isAir() && level.getBlockState(pos).isAir()) return;
        if (level.getBlockState(pos) == p.state()) return;
        level.setBlock(pos, p.state(), p.multipart() ? RoomBuilder.MULTIPART_FLAGS : 3);
    }

    private static void finish(ServerLevel level, Site site, Room room, RoomType type, RoomBuilder.Plan plan) {
        room.built = true;
        PLANS.remove(key(site, room));
        for (BlockPos r : plan.readers) SiteManager.addReader(r);
        if (type.structure != null && !type.structure.isEmpty()) placeStructure(level, room, type);
        String msg = "Construction of " + room.displayName() + " complete."
                + (type.powerDraw > 0 && !room.connected ? " Add it to the Circuit Breaker to power it." : "");
        if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
    }

    /** Datapack rooms can ship a structure template that's stamped into the finished shell. */
    public static void placeStructure(ServerLevel level, Room room, RoomType type) {
        Identifier id = Identifier.tryParse(type.structure);
        if (id == null) return;
        Optional<StructureTemplate> template = level.getServer().getStructureTemplateManager().get(id);
        if (template.isEmpty()) {
            ProjectRedacted.LOG.warn("Room type {} points at missing structure {}", type.id, id);
            return;
        }
        BlockPos origin = new BlockPos(room.x1 + 1, room.y1 + 1, room.z1 + 1);
        template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
    }

    public static void forget(Site site, Room room) {
        PLANS.remove(key(site, room));
    }

    public static void forgetParts(Site site) {
        JOBS.keySet().removeIf(k -> k.startsWith(site.id + "#p"));
    }

    private static String key(Site site, Room room) {
        return site.id + "#" + room.number;
    }

    private Construction() {}
}
