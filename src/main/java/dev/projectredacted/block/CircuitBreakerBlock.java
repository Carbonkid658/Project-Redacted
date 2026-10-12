package dev.projectredacted.block;

import dev.projectredacted.gui.PowerGui;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Rooms stay inert until they're added to the Circuit Breaker. */
public class CircuitBreakerBlock extends FacingBlock {

    public CircuitBreakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, pos);
        if (site == null) {
            Msg.send(player, "This breaker isn't inside a site.", ChatFormatting.GRAY);
        } else if (!site.canAssign(player)) {
            Msg.send(player, "ACCESS DENIED.", ChatFormatting.RED);
        } else {
            PowerGui.open((ServerPlayer) player, site, 0);
        }
        return InteractionResult.SUCCESS;
    }
}
