package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiFunction;

/** Invisible filler for the extra blocks a {@link MultiBlockKey}'s model covers. Has no item. */
public class ModelPartBlock extends FacingBlock {
    private final BiFunction<ModelPartBlock, BlockState, VoxelShape> shape;

    public ModelPartBlock(Properties properties, BiFunction<ModelPartBlock, BlockState, VoxelShape> shape) {
        super(properties);
        this.shape = shape;
    }

    public ModelPartBlock(Properties properties) {
        this(properties, (b, s) -> Shapes.block());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.apply(this, state);
    }

    /** The key block this part belongs to, or null if it's gone. */
    public static BlockPos findMain(BlockGetter level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 0, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MultiBlockKey key && key.footprint(p, s.getValue(FACING)).contains(pos)) return p.immutable();
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos main = findMain(level, pos);
        if (main == null) return InteractionResult.PASS;
        return level.getBlockState(main).useWithoutItem(level, player, hit.withPosition(main));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = findMain(level, pos);
        if (main != null && !level.isClientSide()) level.destroyBlock(main, !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        // Blown up or otherwise removed: take the rest of the model with it.
        if (level.getBlockState(pos).is(this)) return;
        BlockPos main = findMain(level, pos);
        if (main != null) level.destroyBlock(main, true);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        BlockPos main = findMain(level, pos);
        return main == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(main).getBlock());
    }
}
