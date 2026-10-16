package dev.projectredacted.block;

import dev.projectredacted.blockentities.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Key blocks drawn as the desk computer (geckolib/models/computer.geo.json): the terminals and consoles. */
public class ComputerKeyBlock extends KeyBlock implements EntityBlock {
    private static final VoxelShape NORTH_SOUTH = Block.box(0, 0, 1, 16, 13, 15);
    private static final VoxelShape EAST_WEST = Block.box(1, 0, 0, 15, 13, 16);

    public ComputerKeyBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
    }
}
