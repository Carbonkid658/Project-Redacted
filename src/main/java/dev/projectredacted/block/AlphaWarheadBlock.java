package dev.projectredacted.block;

import dev.projectredacted.site.Site;
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

/** The site's nuke. Only the Director can arm it, and only after the Alpha Warhead upgrade. Sneak-use to abort. */
public class AlphaWarheadBlock extends FacingBlock {

    public AlphaWarheadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, pos);
        if (site == null || !site.isDirector(player)) {
            Msg.send(player, "Only the Site Director can operate the Alpha Warhead.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!site.hasUpgrade(Upgrade.ALPHA_WARHEAD)) {
            Msg.send(player, "The warhead is inert. Complete the Alpha Warhead upgrade first.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) Warhead.abort(site);
        else Warhead.arm(site, pos);
        return InteractionResult.SUCCESS;
    }
}
