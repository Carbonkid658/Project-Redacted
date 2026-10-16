package dev.projectredacted.block;

import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The invisible upper half of the two-block-tall Mainframe model. Using or breaking it acts on the Mainframe
 * below, and it disappears when the Mainframe does.
 */
public class MainframeTopBlock extends Block {

    public MainframeTopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (direction == Direction.DOWN && !neighbor.is(ModBlocks.MAINFRAME)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos below = pos.below();
        BlockState mainframe = level.getBlockState(below);
        if (!mainframe.is(ModBlocks.MAINFRAME)) return InteractionResult.PASS;
        return mainframe.useWithoutItem(level, player, hit.withPosition(below));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos below = pos.below();
        if (!level.isClientSide() && level.getBlockState(below).is(ModBlocks.MAINFRAME)) {
            level.destroyBlock(below, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
