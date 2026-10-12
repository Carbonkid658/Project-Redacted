package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.BlockPanels;
import dev.projectredacted.site.Site;
import net.minecraft.server.level.ServerPlayer;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.Upgrade;
import dev.projectredacted.site.Warhead;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The site's nuke. Only the Director can arm it (by typing the site name), and only after the Alpha Warhead upgrade. */
public class AlphaWarheadBlock extends FacingBlock {

    public AlphaWarheadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, pos);
        if (site == null || !site.isDirector(player)) {
            Msg.actionBar(player, "Only the Site Director can operate the Alpha Warhead.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        Gui.open((ServerPlayer) player, new BlockPanels.WarheadPanel(pos));
        return InteractionResult.SUCCESS;
    }
}
