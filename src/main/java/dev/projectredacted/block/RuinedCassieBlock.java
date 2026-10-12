package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.BlockPanels;
import net.minecraft.server.level.ServerPlayer;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.Upgrade;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A broken C.A.S.S.I.E. from an abandoned facility. Cheaper to repair than crafting a new one. */
public class RuinedCassieBlock extends FacingBlock {
    /** Default repair cost (the real cost is still being decided). */
    public static final int REPAIR_KITS = 2;

    public RuinedCassieBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new BlockPanels.Repair(pos, false));
        return InteractionResult.SUCCESS;
    }
}
