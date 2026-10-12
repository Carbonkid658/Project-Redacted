package dev.projectredacted.site.gen;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Construction;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a placed part into blocks (design §6): the terrain work first (clear plants above, foundations below,
 * stone around buried parts), then the part itself, then the doorways it opens in older parts.
 */
public final class PartBuilder {
    /** Deepest a foundation goes before giving up. */
    private static final int MAX_FOUNDATION = 32;

    public static class Job {
        public final List<RoomBuilder.Placement> placements = new ArrayList<>();
        /** Set after the body is placed: connector seals, Turns Into blocks and marker removal (hand-built parts). */
        public final List<RoomBuilder.Placement> fixups = new ArrayList<>();
        public RoomBuilder.Plan body;
        public BlockPos mainframeSpot, terminalSpot;
        public final List<BlockPos> readers = new ArrayList<>();
    }

    public static Job job(ServerLevel level, Site site, int index) {
        PlacedPart pp = site.parts.get(index);
        SitePart part = pp.part();
        Job job = new Job();
        if (part == null) return job;
        Box box = part.box(pp.origin, pp.rot);
        List<Box> others = new ArrayList<>();
        for (int i = 0; i < site.parts.size(); i++) {
            if (i == index || site.parts.get(i).removed) continue;
            Box b = site.parts.get(i).box();
            if (b != null) others.add(b);
        }
        terrain(level, box, others, job.placements);
        for (int i = 0; i < part.connectors.size(); i++) {
            SitePart.Connector c = part.worldConnector(i, pp.origin, pp.rot);
            if (c.type().equals(SitePart.SURFACE_EXIT)) exitPath(level, c, others, job.placements);
        }
        if (part.procedural()) {
            RoomBuilder.Plan plan = ProceduralParts.build(part, pp.origin, pp.rot, pp.used, pp.seed);
            job.body = plan;
            job.placements.addAll(plan.placements);
            job.mainframeSpot = plan.mainframeSpot;
            job.terminalSpot = plan.terminalSpot;
            job.readers.addAll(plan.readers);
        } else {
            template(part, pp, job);
        }
        for (Doorway d : pp.reopen) reopen(d, job.placements);
        return job;
    }

    // ---------------------------------------------------------------- terrain (§6 step 3)

    private static void terrain(ServerLevel level, Box box, List<Box> others, List<RoomBuilder.Placement> out) {
        BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE_BRICKS.defaultBlockState(), rock = Blocks.STONE.defaultBlockState();
        int surface = SiteGen.height(level, (box.x1() + box.x2()) / 2, (box.z1() + box.z2()) / 2);
        boolean buried = box.y2() < surface - 1;
        // Trees and plants just above a surface part.
        if (!buried) {
            for (int x = box.x1(); x <= box.x2(); x++)
                for (int z = box.z1(); z <= box.z2(); z++)
                    for (int y = box.y2() + 1; y <= box.y2() + 4; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!inside(others, p) && SiteGen.vegetation(level.getBlockState(p))) out.add(new RoomBuilder.Placement(p, air, false, false));
                    }
        }
        // Foundations under the floor, down to solid ground.
        for (int x = box.x1(); x <= box.x2(); x++)
            for (int z = box.z1(); z <= box.z2(); z++) {
                for (int d = 1; d <= MAX_FOUNDATION; d++) {
                    BlockPos p = new BlockPos(x, box.y1() - d, z);
                    if (p.getY() <= level.getMinY() || inside(others, p)) break;
                    BlockState s = level.getBlockState(p);
                    if (!(s.isAir() || s.liquid() || s.canBeReplaced() || SiteGen.vegetation(s))) break;
                    out.add(new RoomBuilder.Placement(p, stone, false, true));
                }
            }
        // Buried parts get a skin of stone where caves or water touch them.
        if (buried) {
            Box skin = box.grow(1);
            for (BlockPos p : BlockPos.betweenClosed(skin.x1(), skin.y1(), skin.z1(), skin.x2(), skin.y2(), skin.z2())) {
                if (box.contains(p) || inside(others, p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.liquid() || s.is(Blocks.GRAVEL) || s.is(net.minecraft.tags.BlockTags.SAND)) out.add(new RoomBuilder.Placement(p.immutable(), rock, false, true));
            }
        }
    }

    /**
     * A way out of the Entrance (§3: the surface exit faces open air): a 3-wide path dug out of any hillside in
     * front of the exit, with a solid floor, until it reaches open ground.
     */
    private static void exitPath(ServerLevel level, SitePart.Connector c, List<Box> others, List<RoomBuilder.Placement> out) {
        BlockState air = Blocks.AIR.defaultBlockState(), path = Blocks.STONE_BRICKS.defaultBlockState();
        Direction along = c.facing().getClockWise();
        int floor = c.pos().getY() - 1;
        for (int d = 1; d <= 16; d++) {
            boolean open = true;
            for (int k = -1; k <= 1; k++) {
                BlockPos col = c.pos().relative(c.facing(), d).relative(along, k);
                if (inside(others, col)) continue;
                int ground = SiteGen.height(level, col.getX(), col.getZ());
                if (ground > floor) open = false;
                for (int y = 0; y < 3; y++) {
                    BlockPos p = col.above(y);
                    if (!level.getBlockState(p).isAir()) out.add(new RoomBuilder.Placement(p, air, false, false));
                }
                BlockPos below = col.below();
                BlockState under = level.getBlockState(below);
                if (under.isAir() || under.liquid() || under.canBeReplaced() || SiteGen.vegetation(under)) out.add(new RoomBuilder.Placement(below, path, false, true));
                for (int dy = 2; dy <= 6; dy++) {
                    BlockPos f = col.below(dy);
                    BlockState fs = level.getBlockState(f);
                    if (!(fs.isAir() || fs.liquid() || fs.canBeReplaced())) break;
                    out.add(new RoomBuilder.Placement(f, path, false, true));
                }
            }
            if (open && d >= 2) break;
        }
    }

    private static boolean inside(List<Box> boxes, BlockPos p) {
        for (Box b : boxes) if (b.contains(p)) return true;
        return false;
    }

    // ---------------------------------------------------------------- hand-built parts

    public static Rotation rotation(int r) {
        return switch (r & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void template(SitePart part, PlacedPart pp, Job job) {
        Rotation rotation = rotation(pp.rot);
        BlockState air = Blocks.AIR.defaultBlockState();
        java.util.Map<BlockPos, BlockState> local = new java.util.HashMap<>();
        for (PartTemplate.Entry e : part.template.blocks) local.put(e.pos(), e.state());
        for (PartTemplate.Entry e : part.template.blocks) {
            BlockState s = e.state();
            if (s.is(Blocks.JIGSAW) || s.is(Blocks.STRUCTURE_BLOCK) || s.is(Blocks.STRUCTURE_VOID)) continue;
            BlockPos w = world(pp, e.pos());
            BlockState rs = s.rotate(rotation);
            boolean multi = rs.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || rs.hasProperty(BlockStateProperties.BED_PART);
            job.placements.add(new RoomBuilder.Placement(w, rs, multi, !rs.isAir()));
        }
        for (int i = 0; i < part.connectors.size(); i++) {
            SitePart.Connector lc = part.connectors.get(i);
            SitePart.Connector c = part.worldConnector(i, pp.origin, pp.rot);
            if ((pp.used & (1 << i)) != 0) {
                job.fixups.add(new RoomBuilder.Placement(c.pos(), turnsInto(lc.turnsInto()), false, false));
            } else {
                // Seal the unused doorway with the wall block beside it.
                Direction along = lc.vertical() ? lc.top().getClockWise() : lc.facing().getClockWise();
                int half = lc.width() / 2;
                BlockState wall = local.getOrDefault(lc.pos().relative(along, half + 1), ModBlocks.FACILITY_WALL.defaultBlockState()).rotate(rotation);
                if (wall.isAir() || wall.is(Blocks.JIGSAW)) wall = ModBlocks.FACILITY_WALL.defaultBlockState();
                int height = lc.vertical() ? 1 : lc.height();
                for (int k = -half; k <= half; k++)
                    for (int y = 0; y < height; y++) job.fixups.add(new RoomBuilder.Placement(world(pp, lc.pos().relative(along, k).above(y)), wall, false, true));
            }
        }
        for (SitePart.Marker m : part.markers) job.fixups.add(new RoomBuilder.Placement(world(pp, m.pos()), air, false, false));
        if (part.mainframe != null) job.mainframeSpot = world(pp, part.mainframe);
        if (part.terminal != null) job.terminalSpot = world(pp, part.terminal);
        for (BlockPos r : part.readers) job.readers.add(world(pp, r));
    }

    static BlockPos world(PlacedPart pp, BlockPos local) {
        return pp.origin.offset(SitePart.rotate(local, pp.rot));
    }

    /** The block a used connector turns into, from its "Turns Into" text (properties are ignored). */
    public static BlockState turnsInto(String text) {
        if (text == null || text.isBlank()) return Blocks.AIR.defaultBlockState();
        String id = text.contains("[") ? text.substring(0, text.indexOf('[')) : text;
        Identifier key = Identifier.tryParse(id.trim());
        if (key == null) return Blocks.AIR.defaultBlockState();
        Block b = BuiltInRegistries.BLOCK.getValue(key);
        return b == null ? Blocks.AIR.defaultBlockState() : b.defaultBlockState();
    }

    // ---------------------------------------------------------------- reopening older doorways

    private static void reopen(Doorway d, List<RoomBuilder.Placement> out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        SitePart.Connector c = d.connector();
        if (c.vertical()) {
            Direction across = c.top().getClockWise();
            for (int k = -1; k <= 1; k++) out.add(new RoomBuilder.Placement(c.pos().relative(across, k), air, false, false));
            out.add(new RoomBuilder.Placement(c.pos(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, c.top()), false, true));
            return;
        }
        Direction along = c.facing().getClockWise();
        int half = c.width() / 2;
        for (int k = -half; k <= half; k++)
            for (int y = 0; y < c.height(); y++) out.add(new RoomBuilder.Placement(c.pos().relative(along, k).above(y), air, false, false));
    }

    // ---------------------------------------------------------------- finishing

    public static void finish(ServerLevel level, Site site, int index, Job job, boolean register) {
        PlacedPart pp = site.parts.get(index);
        SitePart part = pp.part();
        if (part != null && part.template != null) {
            // Vanilla placement restores chests, signs and other block data; then the doorways are fixed up again.
            try {
                StructureTemplate t = new StructureTemplate();
                t.load(BuiltInRegistries.BLOCK, part.template.tag);
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation(pp.rot)).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(false);
                t.placeInWorld(level, pp.origin, pp.origin, settings, level.getRandom(), 2);
            } catch (RuntimeException e) {
                ProjectRedacted.LOG.warn("Couldn't place block data for part {}", part, e);
            }
            for (RoomBuilder.Placement p : job.fixups) Construction.place(level, p);
        }
        if (register) for (BlockPos r : job.readers) SiteManager.addReader(r);
        for (Room r : SiteGen.rooms(site, pp)) {
            r.built = true;
            r.buildProgress = 0;
            RoomType type = r.typeDef();
            if (type != null && type.structure != null && !type.structure.isEmpty() && part != null && part.procedural()) Construction.placeStructure(level, r, type);
        }
        if (part != null) markers(site, pp, part);
        if (pp.fast && job.mainframeSpot != null) moveCore(level, site, job);
        pp.built = true;
    }

    private static void markers(Site site, PlacedPart pp, SitePart part) {
        for (SitePart.Marker m : part.markers) {
            String name = m.name().contains(":") ? m.name().substring(0, m.name().indexOf(':')) : m.name();
            BlockPos w = world(pp, m.pos());
            switch (name) {
                case "raider_entry" -> {
                    if (!site.raiderEntries.contains(w)) site.raiderEntries.add(w);
                }
                case "staff_post" -> {
                    if (!site.staffPosts.contains(w)) site.staffPosts.add(w);
                }
                default -> {}
            }
        }
    }

    /** Moves the Mainframe and Terminal into the new Director's Office. */
    private static void moveCore(ServerLevel level, Site site, Job job) {
        BlockPos oldMain = site.mainframe;
        if (!job.mainframeSpot.equals(oldMain)) {
            if (oldMain != null && level.getBlockState(oldMain).is(ModBlocks.MAINFRAME)) level.setBlock(oldMain, Blocks.AIR.defaultBlockState(), 3);
            if (!level.getBlockState(job.mainframeSpot).is(ModBlocks.MAINFRAME))
                level.setBlock(job.mainframeSpot, ModBlocks.MAINFRAME.defaultBlockState().setValue(FacingBlock.FACING, Direction.NORTH), 3);
            site.mainframe = job.mainframeSpot;
        }
        if (job.terminalSpot != null) {
            if (site.terminal != null && !site.terminal.equals(job.terminalSpot) && level.getBlockState(site.terminal).is(ModBlocks.SITE_DIRECTOR_COMPUTER)) {
                level.setBlock(site.terminal, Blocks.AIR.defaultBlockState(), 3);
            }
            if (!level.getBlockState(job.terminalSpot).is(ModBlocks.SITE_DIRECTOR_COMPUTER))
                level.setBlock(job.terminalSpot, ModBlocks.SITE_DIRECTOR_COMPUTER.defaultBlockState().setValue(FacingBlock.FACING, Direction.SOUTH), 3);
            site.terminal = job.terminalSpot;
        }
        site.mainframePresent = true;
    }

    /** Containers being replaced spill their items instead of losing them. */
    public static void dropContents(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c && !c.isEmpty()) Containers.dropContents(level, pos, c);
    }

    private PartBuilder() {}
}
