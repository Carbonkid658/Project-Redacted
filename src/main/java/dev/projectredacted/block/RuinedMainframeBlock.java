package dev.projectredacted.block;

import com.mojang.serialization.MapCodec;
import dev.projectredacted.registry.ModItems;
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
    public static final MapCodec<RuinedMainframeBlock> CODEC = simpleCodec(RuinedMainframeBlock::new);
    public static final int REPAIR_KITS = 3;

    public RuinedMainframeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.REPAIR_KIT)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!player.isCreative() && stack.getCount() < REPAIR_KITS) {
            Msg.send(player, "Repairing this Mainframe takes " + REPAIR_KITS + " Repair Kits.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        if (SiteFounding.repairRuinedMainframe((ServerLevel) level, pos, (ServerPlayer) player)) {
            if (!player.isCreative()) stack.shrink(REPAIR_KITS);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            Msg.send(player, "A dead Foundation Mainframe. " + REPAIR_KITS + " Repair Kits could bring it back.", ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }
}
