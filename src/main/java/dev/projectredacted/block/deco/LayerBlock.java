package dev.projectredacted.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stacks up to eight layers when you place more on it, like snow (rubble). */
public class LayerBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];

    static {
        for (int i = 0; i <= 8; i++) SHAPES[i] = Shapes.box(0, 0, 0, 1, i * 2 / 16.0, 1);
    }

    public LayerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (!context.getItemInHand().is(asItem()) || layers >= 8) return false;
        return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) return here.setValue(LAYERS, Math.min(8, here.getValue(LAYERS) + 1));
        return defaultBlockState();
    }
}
