package dev.projectredacted.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** A two-block-high shaped block (lockers, server racks, vending machines). Its boxes run from y = 0 to 32. */
public class TallShapedBlock extends ShapedBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private final Map<Direction, VoxelShape> lower = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> upper = new EnumMap<>(Direction.class);

    public TallShapedBlock(Properties properties, Spec spec) {
        super(properties, spec);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            lower.put(d, shape(spec.boxes(), d, 0, 16));
            upper.put(d, shape(spec.boxes(), d, 16, 32));
        }
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected VoxelShape shapeFor(BlockState state) {
        Direction d = state.getValue(FACING);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? lower.get(d) : upper.get(d);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOther && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking the top in creative would otherwise drop the bottom half.
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
