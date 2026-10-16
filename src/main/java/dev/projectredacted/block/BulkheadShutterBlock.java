package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The steel shutter an Emergency Bulkhead drops. It can't be mined; it goes away when the bulkhead opens, and
 * on its own if it's no longer hanging from a closed bulkhead.
 */
public class BulkheadShutterBlock extends FacingBlock {
    private static final VoxelShape ACROSS_Z = Block.box(0, 0, 6, 16, 16, 10), ACROSS_X = Block.box(6, 0, 0, 10, 16, 16);

    public BulkheadShutterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_Z : ACROSS_X;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighborPos,
                                     BlockState neighbor, RandomSource random) {
        if (dir != Direction.UP) return state;
        boolean held = neighbor.is(this) || neighbor.getBlock() instanceof EmergencyBulkheadBlock && neighbor.getValue(EmergencyBulkheadBlock.CLOSED);
        return held ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
