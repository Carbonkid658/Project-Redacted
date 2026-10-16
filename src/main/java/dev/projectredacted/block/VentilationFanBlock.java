package dev.projectredacted.block;

import dev.projectredacted.blockentities.VentilationFanBlockEntity;
import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Ventilation room's key block: a 2 by 2 wall fan with spinning blades
 * (geckolib/models/ventilation_fan.geo.json). Place it against a wall; it covers this block, the one to your
 * right, and the two above them.
 */
public class VentilationFanBlock extends MultiBlockKey {
    private static final int[][] OFFSETS = {{1, 0, 0}, {0, 1, 0}, {1, 1, 0}};
    /** The fan housing is 6 pixels deep, against the wall behind it. */
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 10, 16, 16, 16), // north: the wall is to the south
            Block.box(0, 0, 0, 16, 16, 6),   // south
            Block.box(10, 0, 0, 16, 16, 16), // west
            Block.box(0, 0, 0, 6, 16, 16)};  // east

    public VentilationFanBlock(Properties properties) {
        super(properties, () -> ModBlocks.VENTILATION_FAN_PART);
    }

    public static VoxelShape shape(Direction facing) {
        return switch (facing) {
            case SOUTH -> SHAPES[1];
            case WEST -> SHAPES[2];
            case EAST -> SHAPES[3];
            default -> SHAPES[0];
        };
    }

    @Override
    protected int[][] offsets() {
        return OFFSETS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentilationFanBlockEntity(pos, state);
    }
}
