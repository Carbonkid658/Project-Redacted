package dev.projectredacted.room;

import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Room shape detection (site generation v4 §5.7): flood-fills a room's air space from a seed until it meets
 * walls, floor and ceiling. Doors, trapdoors, glass, jigsaws and markers count as wall, and so does the doorway
 * above a marker or jigsaw. The space is closed only if the fill never runs out of the scan area.
 */
public final class RoomShape {
    public enum Cell { OPEN, WALL, OUTSIDE }

    /** What's at a position: open space, wall, or outside what may be searched (a leak). */
    public interface Grid {
        Cell at(BlockPos pos);

        /** The block there, for noting key blocks in the walls; null if unknown. */
        BlockState state(BlockPos pos);
    }

    /**
     * The result. {@code interior} holds the open cells (as {@link BlockPos#asLong}); {@code walls} the wall cells
     * touching them, which is where key blocks are found. {@code leak} is set when the space isn't closed.
     */
    public record Result(Set<Long> interior, Set<Long> walls, BlockPos leak, boolean tooBig) {
        public boolean closed() {
            return leak == null && !interior.isEmpty();
        }

        /** The box around the space with its walls: floor corner to ceiling corner, like a {@link Room}. */
        public int[] box() {
            int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
            for (long l : interior) {
                int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
                x1 = Math.min(x1, x); y1 = Math.min(y1, y); z1 = Math.min(z1, z);
                x2 = Math.max(x2, x); y2 = Math.max(y2, y); z2 = Math.max(z2, z);
            }
            return new int[]{x1 - 1, y1 - 1, z1 - 1, x2 + 1, y2 + 1, z2 + 1};
        }

        public boolean contains(BlockPos pos) {
            return interior.contains(pos.asLong());
        }
    }

    /** Never searches more cells than this, whatever the scan area. */
    private static final int MAX_CELLS = 200_000;

    public static Result fill(Grid grid, Collection<BlockPos> seeds) {
        Set<Long> interior = new HashSet<>(), walls = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos s : seeds) {
            Cell c = grid.at(s);
            if (c == Cell.OUTSIDE) return new Result(Set.of(), Set.of(), s.immutable(), false);
            if (c == Cell.OPEN && interior.add(s.asLong())) queue.add(s.immutable());
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                long key = n.asLong();
                if (interior.contains(key)) continue;
                Cell c = grid.at(n);
                if (c == Cell.OUTSIDE) return new Result(interior, walls, n, false);
                if (c == Cell.WALL) {
                    walls.add(key);
                    continue;
                }
                interior.add(key);
                if (interior.size() > MAX_CELLS) return new Result(interior, walls, n, true);
                queue.add(n);
            }
        }
        return new Result(interior, walls, null, false);
    }

    /** Where a fill starts from a block that is itself solid (a key block): the open cells next to it. */
    public static List<BlockPos> around(BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction d : Direction.values()) out.add(pos.relative(d));
        return out;
    }

    /** Where a fill starts from a Room Entrance Marker: the block behind its arrow, inside the room. */
    public static BlockPos behindMarker(BlockPos pos, Direction facing) {
        return pos.relative(facing.getOpposite());
    }

    // ---------------------------------------------------------------- what counts as wall

    /** A block that seals a room: anything solid, plus doors, trapdoors, gates, glass, jigsaws and markers. */
    public static boolean isWall(BlockGetter level, BlockPos pos, BlockState s) {
        if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock || s.getBlock() instanceof FenceGateBlock
                || s.getBlock() instanceof dev.projectredacted.block.WallFrameBlock) return true;
        if (isPlug(s) || s.is(BlockTags.IMPERMEABLE)) return true;
        return !s.getCollisionShape(level, pos).isEmpty();
    }

    /** Markers and jigsaws close the doorway they stand in, along with the two blocks above them. */
    public static boolean isPlug(BlockState s) {
        return s.is(ModBlocks.ROOM_ENTRANCE_MARKER) || s.is(Blocks.JIGSAW);
    }

    /** A grid over the world, limited to a cube of {@code size} blocks centered on {@code center} (V22). */
    public static Grid world(BlockGetter level, BlockPos center, int size) {
        int half = Math.max(2, size / 2);
        return new Grid() {
            @Override
            public Cell at(BlockPos p) {
                if (Math.abs(p.getX() - center.getX()) > half || Math.abs(p.getY() - center.getY()) > half || Math.abs(p.getZ() - center.getZ()) > half) return Cell.OUTSIDE;
                BlockState s = level.getBlockState(p);
                if (isWall(level, p, s)) return Cell.WALL;
                if (isPlug(level.getBlockState(p.below())) || isPlug(level.getBlockState(p.below(2)))) return Cell.WALL;
                return Cell.OPEN;
            }

            @Override
            public BlockState state(BlockPos p) {
                return level.getBlockState(p);
            }
        };
    }

    private RoomShape() {}
}
