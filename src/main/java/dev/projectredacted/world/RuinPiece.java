package dev.projectredacted.world;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/** One part of a ruined site, placed chunk by chunk and then decayed (design §9). */
public class RuinPiece extends StructurePiece {
    public static StructurePieceType TYPE;

    private final String kit, id;
    private final BlockPos origin;
    private final int rot, used;
    private final long seed;
    /** The office piece carries every room of the ruin, to register it once its Mainframe is placed. */
    private final List<Room> rooms;

    public RuinPiece(SitePlan.PlanPart pp, long seed, List<Room> rooms) {
        super(TYPE, 0, box(pp.box));
        this.kit = pp.part.kit;
        this.id = pp.part.id;
        this.origin = pp.origin;
        this.rot = pp.rot;
        this.used = pp.used;
        this.seed = seed;
        this.rooms = new ArrayList<>(rooms);
    }

    public RuinPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(TYPE, tag);
        kit = tag.getStringOr("kit", "placeholder");
        id = tag.getStringOr("part", "");
        origin = new BlockPos(tag.getIntOr("ox", 0), tag.getIntOr("oy", 0), tag.getIntOr("oz", 0));
        rot = tag.getIntOr("rot", 0);
        used = tag.getIntOr("used", 0);
        seed = tag.getLongOr("seed", 0L);
        rooms = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("rooms");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag r = list.getCompoundOrEmpty(i);
            int[] b = r.getIntArray("box").orElse(new int[6]);
            if (b.length < 6) continue;
            rooms.add(new Room(0, r.getStringOr("type", ""), new BlockPos(b[0], b[1], b[2]), new BlockPos(b[3], b[4], b[5]), r.getIntOr("rot", 0), seed));
        }
    }

    @Override
    public String toString() {
        return kit + "/" + id + " rot " + rot + " rooms " + rooms.size();
    }

    private static BoundingBox box(Box b) {
        return new BoundingBox(b.x1(), b.y1(), b.z1(), b.x2(), b.y2(), b.z2());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("kit", kit);
        tag.putString("part", id);
        tag.putInt("ox", origin.getX());
        tag.putInt("oy", origin.getY());
        tag.putInt("oz", origin.getZ());
        tag.putInt("rot", rot);
        tag.putInt("used", used);
        tag.putLong("seed", seed);
        ListTag list = new ListTag();
        for (Room r : rooms) {
            CompoundTag c = new CompoundTag();
            c.putString("type", r.type);
            c.put("box", new IntArrayTag(new int[]{r.x1, r.y1, r.z1, r.x2, r.y2, r.z2}));
            c.putInt("rot", r.rotation);
            list.add(c);
        }
        tag.put("rooms", list);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        SitePart part = PartKit.active().byId(kit, id);
        if (part == null) {
            dev.projectredacted.ProjectRedacted.LOG.warn("[parts] ruin part {}/{} not found", kit, id);
            return;
        }
        PlacedPart pp = new PlacedPart();
        pp.kit = kit;
        pp.id = id;
        pp.origin = origin;
        pp.rot = rot;
        pp.used = used;
        pp.seed = seed;
        PartBuilder.Job job = new PartBuilder.Job();
        PartBuilder.body(part, pp, job);
        for (RoomBuilder.Placement p : job.placements) {
            if (chunkBox.isInside(p.pos())) level.setBlock(p.pos(), p.state(), p.multipart() ? RoomBuilder.MULTIPART_FLAGS : 2);
        }
        if (part.template != null) {
            StructureTemplate t = new StructureTemplate();
            t.load(BuiltInRegistries.BLOCK, part.template.tag);
            t.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(PartBuilder.rotation(rot)).setRotationPivot(BlockPos.ZERO)
                    .setBoundingBox(chunkBox), random, 2);
            for (RoomBuilder.Placement p : job.fixups) if (chunkBox.isInside(p.pos())) level.setBlock(p.pos(), p.state(), 2);
        }
        Box b = part.box(origin, rot);
        foundations(level, chunkBox, b);
        decay(level, chunkBox, b, job, part.procedural());
        for (SitePart.Marker m : part.markers) {
            BlockPos w = PartBuilder.world(pp, m.pos());
            if (m.name().startsWith("loot") && chunkBox.isInside(w)) GenUtil.lootChest(level, w, RandomSource.create(seed ^ w.asLong()), true);
        }
        if (!rooms.isEmpty() && job.mainframeSpot == null) dev.projectredacted.ProjectRedacted.LOG.warn("[parts] ruin office {} has no Mainframe spot", id);
        if (job.mainframeSpot != null && chunkBox.isInside(job.mainframeSpot)) {
            level.setBlock(job.mainframeSpot, ModBlocks.RUINED_MAINFRAME.defaultBlockState().setValue(FacingBlock.FACING, Direction.SOUTH), 2);
            if (job.terminalSpot != null && Math.floorMod(seed, 3) == 0)
                level.setBlock(job.terminalSpot, ModBlocks.RUINED_CASSIE.defaultBlockState().setValue(dev.projectredacted.block.CassieBlock.ROTATION,
                        net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(Direction.SOUTH)), 2);
            BlockPos chest = new BlockPos(b.x2() - 1, b.y1() + 1, b.z1() + 1);
            if (chunkBox.isInside(chest) && !chest.equals(job.mainframeSpot) && !chest.equals(job.terminalSpot))
                GenUtil.lootChest(level, chest, RandomSource.create(seed), true);
            SiteManager.RuinRecord record = new SiteManager.RuinRecord();
            record.mainframe = job.mainframeSpot;
            record.rooms.addAll(rooms);
            SiteManager.queueRuin(record);
            dev.projectredacted.ProjectRedacted.LOG.info("[parts] ruined site generated with its Mainframe at {}", job.mainframeSpot.toShortString());
        }
    }

    /** Holds the floor up where the ground dips, down to solid ground. */
    private static void foundations(WorldGenLevel level, BoundingBox chunkBox, Box b) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = b.x1(); x <= b.x2(); x++)
            for (int z = b.z1(); z <= b.z2(); z++) {
                if (!chunkBox.isInside(x, b.y1(), z)) continue;
                for (int d = 1; d <= 12; d++) {
                    BlockPos p = new BlockPos(x, b.y1() - d, z);
                    BlockState s = level.getBlockState(p);
                    if (!(s.isAir() || s.liquid() || s.canBeReplaced())) break;
                    level.setBlock(p, (x + z + d) % 3 == 0 ? cobble : stone, 2);
                }
            }
    }

    /** The decay pass: walls crumble, cobblestone patches, cobwebs and moss (the old ruin rules). */
    private void decay(WorldGenLevel level, BoundingBox chunkBox, Box b, PartBuilder.Job job, boolean procedural) {
        // Only walls, floors and ceilings decay, so furniture stays whole.
        java.util.Set<BlockPos> structural = new java.util.HashSet<>();
        if (procedural) for (RoomBuilder.Placement p : job.placements) if (p.structural() && !p.state().isAir()) structural.add(p.pos());
        for (BlockPos pos : BlockPos.betweenClosed(b.x1(), b.y1(), b.z1(), b.x2(), b.y2(), b.z2())) {
            if (!chunkBox.isInside(pos)) continue;
            if (procedural && !structural.contains(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.hasBlockEntity() || s.getBlock() instanceof DoorBlock || s.is(Blocks.LADDER)) continue;
            if (!procedural && !s.isCollisionShapeFullBlock(level, pos)) continue;
            if (pos.equals(job.mainframeSpot) || pos.equals(job.terminalSpot)) continue;
            int roll = Math.floorMod(Long.hashCode(seed * 31 + pos.asLong() * 0x9E3779B97F4A7C15L), 100);
            int y = pos.getY();
            if (roll < 15 && y > b.y1()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // crumbled away
            else if (roll < 25 && y > b.y1()) level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            else if (roll < 30 && y > b.y1() + 1 && y < b.y2()) level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
            else if (roll < 34 && y == b.y1()) level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        }
    }
}
