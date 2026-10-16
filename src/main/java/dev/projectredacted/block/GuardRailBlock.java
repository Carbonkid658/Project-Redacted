package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A railing along one edge of its block (the side the player faced when placing it), for catwalks and
 * balconies. Its collision is a block and a half tall, like a fence, so nobody hops over it.
 */
public class GuardRailBlock extends FacingBlock {
    private static final VoxelShape[] SHAPES = new VoxelShape[4], COLLISION = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int i = d.get2DDataValue();
            SHAPES[i] = edge(d, 16);
            COLLISION[i] = edge(d, 24);
        }
    }

    private static VoxelShape edge(Direction d, double height) {
        return switch (d) {
            case NORTH -> Block.box(0, 0, 0, 16, height, 2);
            case SOUTH -> Block.box(0, 0, 14, 16, height, 16);
            case WEST -> Block.box(0, 0, 0, 2, height, 16);
            default -> Block.box(14, 0, 0, 16, height, 16);
        };
    }

    public GuardRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
