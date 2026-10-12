package dev.projectredacted.block;

import dev.projectredacted.item.KeycardItem;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.site.DoorControl;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Opens nearby iron or reinforced doors for a keycard of at least {@link #LEVEL}. Staff with enough clearance
 * are let through automatically. The Director changes the level by using a Clipboard on it.
 */
public class KeycardReaderBlock extends FacingBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    public KeycardReaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) SiteManager.addReader(pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.CLIPBOARD)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Site site = SiteManager.at(level, pos);
            if (site != null && site.isDirector(player)) {
                int next = state.getValue(LEVEL) % 5 + 1;
                level.setBlock(pos, state.setValue(LEVEL, next), 3);
                SiteManager.addReader(pos);
                Msg.send(player, "Reader now requires Level " + next + " clearance.", ChatFormatting.AQUA);
            } else {
                Msg.send(player, "Only the Site Director can reprogram readers.", ChatFormatting.RED);
            }
            return InteractionResult.SUCCESS;
        }
        if (!(stack.getItem() instanceof KeycardItem card)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        SiteManager.addReader(pos);
        if (card.level() >= state.getValue(LEVEL)) {
            DoorControl.openNear(level, pos, 60);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.6f);
        } else {
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6f, 0.6f);
            Msg.actionBar(player, "ACCESS DENIED. Level " + state.getValue(LEVEL) + " required.", ChatFormatting.RED);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Msg.actionBar(player, "Insert a Level " + state.getValue(LEVEL) + "+ keycard.", ChatFormatting.YELLOW);
        return InteractionResult.SUCCESS;
    }
}
