package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall Frame: fills a wide doorway so the room behind it still counts as closed, without a door. It has no
 * collision. Frames next to each other join up: each one only draws the edge bars on sides where there's no
 * other frame, so a 3×3 opening filled with them shows one frame around the whole doorway. The bottom edge
 * is left off where it stands on a floor.
 * <p>
 * {@code AXIS} is the way the wall runs (X: the wall faces north and south). The four sides are {@code UP},
 * {@code DOWN}, and {@code LEFT}/{@code RIGHT} along the axis (west/east, or north/south); true means joined.
 */
public class WallFrameBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
    /** West (axis X) or north (axis Z). */
    public static final BooleanProperty LEFT = BlockStateProperties.WEST;
    /** East (axis X) or south (axis Z). */
    public static final BooleanProperty RIGHT = BlockStateProperties.EAST;

    private static final VoxelShape PLANE_X = Block.box(0, 0, 7, 16, 16, 9), PLANE_Z = Block.box(7, 0, 0, 9, 16, 16);

    public WallFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, UP, DOWN, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The wall runs across the player's view: looking north, it runs east-west.
        Direction.Axis axis = context.getHorizontalDirection().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        return joined(defaultBlockState().setValue(AXIS, axis), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos neighborPos,
                                     BlockState neighbor, RandomSource random) {
        return joined(state, level, pos);
    }

    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction left = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        BlockState below = level.getBlockState(pos.below());
        return state.setValue(UP, same(level.getBlockState(pos.above()), axis))
                .setValue(DOWN, same(below, axis) || below.isFaceSturdy(level, pos.below(), Direction.UP))
                .setValue(LEFT, same(level.getBlockState(pos.relative(left)), axis))
                .setValue(RIGHT, same(level.getBlockState(pos.relative(left.getOpposite())), axis));
    }

    private boolean same(BlockState s, Direction.Axis axis) {
        return s.getBlock() == this && s.getValue(AXIS) == axis;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? PLANE_X : PLANE_Z;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
