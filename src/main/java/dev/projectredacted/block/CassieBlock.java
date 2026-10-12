package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.BlockPanels;
import net.minecraft.server.level.ServerPlayer;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.Upgrade;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * C.A.S.S.I.E., the site's announcer and early-warning system. It keeps running when the Mainframe is down,
 * which is why the design calls it the safeguard. Raiders can hack it; Repair Kits restore it.
 */
public class CassieBlock extends FacingBlock {

    public CassieBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)) return;
        Site site = SiteManager.at(level, pos);
        if (site == null || !site.isDirector(player)) {
            Msg.send(player, "C.A.S.S.I.E. only works inside a site you direct.", ChatFormatting.YELLOW);
            return;
        }
        if (!site.hasUpgrade(Upgrade.CASSIE_PROGRAM) && !player.isCreative()) {
            Msg.send(player, "Unlock the C.A.S.S.I.E. Program upgrade at the terminal first.", ChatFormatting.RED);
            level.destroyBlock(pos, true);
            return;
        }
        site.cassie = pos.immutable();
        site.cassieHack = 0;
        site.cassieDisabled = false;
        site.log("C.A.S.S.I.E. installed.");
        dev.projectredacted.site.Cassie.announce(site, "C.A.S.S.I.E. online. All systems nominal.");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new BlockPanels.CassieConsole(pos));
        return InteractionResult.SUCCESS;
    }
}
