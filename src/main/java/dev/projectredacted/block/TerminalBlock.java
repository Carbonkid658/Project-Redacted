package dev.projectredacted.block;

import dev.projectredacted.blockentities.SiteDirectorComputerBlockEntity;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.TerminalScreen;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Site Director's Computer: the Mainframe Terminal, the site's main interface. Drawn by GeckoLib. */
public class TerminalBlock extends FacingBlock implements EntityBlock {
    // The desk is 13 pixels tall and nearly a full block wide; narrower front to back.
    private static final VoxelShape NORTH_SOUTH = Block.box(0, 0, 1, 16, 13, 15);
    private static final VoxelShape EAST_WEST = Block.box(1, 0, 0, 15, 13, 16);

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SiteDirectorComputerBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
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
        Gui.open((ServerPlayer) player, new TerminalScreen(site, pos));
        return InteractionResult.SUCCESS;
    }
}
