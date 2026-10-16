package dev.projectredacted.block;

import dev.projectredacted.blockentities.MainframeBlockEntity;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.gui.screen.BlockPanels;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteFounding;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The heart of a site. Placing one founds the player's site; while it is offline the site stops running.
 * Drawn by GeckoLib (geckolib/models/mainframe.geo.json). The model is two blocks tall, so an invisible
 * {@link MainframeTopBlock} fills the space above it.
 */
public class MainframeBlock extends FacingBlock implements EntityBlock {

    public MainframeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MainframeBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Site generation places Mainframes with setBlock, so the top goes in here rather than in setPlacedBy.
        BlockPos above = pos.above();
        if (!level.isClientSide() && !oldState.is(this) && level.getBlockState(above).canBeReplaced()) {
            level.setBlock(above, ModBlocks.MAINFRAME_TOP.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            SiteFounding.onMainframePlaced(serverLevel, pos, player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new BlockPanels.Mainframe(pos));
        return InteractionResult.SUCCESS;
    }
}
