package dev.projectredacted.block;

import com.mojang.serialization.MapCodec;
import dev.projectredacted.gui.TerminalGui;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Site Director's Computer: the Mainframe Terminal, the site's main interface. */
public class TerminalBlock extends FacingBlock {
    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        Site site = SiteManager.at(level, pos);
        if (site != null && placer instanceof Player player && site.isDirector(player)) {
            site.terminal = pos.immutable();
            Msg.send(player, "Terminal linked to your site's Mainframe.", ChatFormatting.GREEN);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, pos);
        if (site == null) {
            Msg.send(player, "No site here. Place a Mainframe first to found your site.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        if (!site.canUseTerminal(player)) {
            Msg.send(player, "ACCESS DENIED. Your clearance doesn't allow terminal access.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!site.mainframeOnline()) {
            Msg.send(player, "The Mainframe is offline. Repair or rebuild it to use the terminal.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        TerminalGui.openMain((ServerPlayer) player, site);
        return InteractionResult.SUCCESS;
    }
}
