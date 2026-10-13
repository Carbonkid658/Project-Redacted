package dev.projectredacted.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * A shaped light that redstone switches. Ordinary lights are on until powered (so a lever kills a corridor);
 * alarm lights (emergency beacons) are off until powered, so breach and lockdown circuits can light them.
 */
public class LightBlock extends ShapedBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final boolean alarm;

    public LightBlock(Properties properties, Spec spec, boolean alarm) {
        super(properties, spec);
        this.alarm = alarm;
        registerDefaultState(defaultBlockState().setValue(LIT, !alarm));
    }

    /** Light level for the properties: full when lit, none when not. */
    public static int light(BlockState state, int level) {
        return state.getValue(LIT) ? level : 0;
    }

    private boolean litFor(boolean powered) {
        return alarm == powered;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LIT, litFor(context.getLevel().hasNeighborSignal(context.getClickedPos())));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean lit = litFor(level.hasNeighborSignal(pos));
        if (lit != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
    }

    @Override
    protected boolean showParticles(BlockState state) {
        return state.getValue(LIT);
    }
}
