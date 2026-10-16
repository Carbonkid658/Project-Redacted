package dev.projectredacted.block;

import dev.projectredacted.blockentities.DyeStationBlockEntity;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.DyeStationScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Dyes dyeable blocks (and leather) any color. Right-click with a dyeable item to put the stack in, or empty-handed
 * to open the color wheel; the stack can also be picked from your inventory on the screen.
 */
public class DyeStationBlock extends FacingBlock implements EntityBlock {
    public DyeStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DyeStationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!DyeStationBlockEntity.dyeable(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DyeStationBlockEntity station) {
            player.setItemInHand(hand, station.insert(player, stack));
            Gui.open((ServerPlayer) player, new DyeStationScreen(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DyeStationBlockEntity) Gui.open((ServerPlayer) player, new DyeStationScreen(pos));
        return InteractionResult.SUCCESS;
    }
}
