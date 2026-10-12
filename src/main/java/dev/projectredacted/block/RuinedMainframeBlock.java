package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.BlockPanels;
import dev.projectredacted.site.SiteFounding;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Found in old, ruined sites. Repair it with Repair Kits to found your site inside the old facility. */
public class RuinedMainframeBlock extends FacingBlock {
    public static final int REPAIR_KITS = 3;

    public RuinedMainframeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new BlockPanels.Repair(pos, true));
        return InteractionResult.SUCCESS;
    }
}
