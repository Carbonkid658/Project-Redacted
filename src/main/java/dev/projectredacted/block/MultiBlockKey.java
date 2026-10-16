package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A key block whose GeckoLib model is bigger than one block (the Conference Table, the Ventilation Fan). The
 * other blocks it covers are filled with invisible {@link ModelPartBlock}s, which pass clicks on to it and break it
 * when they are broken.
 */
public abstract class MultiBlockKey extends KeyBlock implements EntityBlock {
    private final Supplier<Block> part;

    protected MultiBlockKey(Properties properties, Supplier<Block> part) {
        super(properties, false);
        this.part = part;
    }

    /**
     * The blocks the model covers, other than this one, as (right, up, back) offsets for a block facing north.
     * "Right" and "back" are as seen by the player who placed it.
     */
    protected abstract int[][] offsets();

    /** The covered blocks, including {@code pos}. */
    public List<BlockPos> footprint(BlockPos pos, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        out.add(pos);
        for (int[] o : offsets()) out.add(pos.offset(rotate(o, facing)));
        return out;
    }

    /**
     * Offsets in the model are for a block facing north (the player stands to the north looking south, so their
     * right is west). GeckoLib turns the model the same way for the other directions.
     */
    private static BlockPos rotate(int[] o, Direction facing) {
        int x = -o[0], y = o[1], z = o[2];
        return switch (facing) {
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            case EAST -> new BlockPos(-z, y, x);
            default -> new BlockPos(x, y, z);
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Level level = context.getLevel();
        for (BlockPos p : footprint(context.getClickedPos(), state.getValue(FACING))) {
            if (!p.equals(context.getClickedPos()) && !level.getBlockState(p).canBeReplaced(context)) return null;
        }
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level.isClientSide() || old.is(this)) return;
        BlockState partState = part.get().defaultBlockState().setValue(FACING, state.getValue(FACING));
        for (BlockPos p : footprint(pos, state.getValue(FACING))) {
            if (!p.equals(pos) && level.getBlockState(p).canBeReplaced()) level.setBlock(p, partState, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (level.getBlockState(pos).is(this)) return;
        for (BlockPos p : footprint(pos, state.getValue(FACING))) {
            if (level.getBlockState(p).getBlock() instanceof ModelPartBlock) level.removeBlock(p, false);
        }
    }
}
