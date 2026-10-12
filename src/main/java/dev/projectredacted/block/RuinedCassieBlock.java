package dev.projectredacted.block;

import com.mojang.serialization.MapCodec;
import dev.projectredacted.registry.ModBlocks;
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
    public static final MapCodec<RuinedCassieBlock> CODEC = simpleCodec(RuinedCassieBlock::new);
    /** Default repair cost (the real cost is still being decided). */
    public static final int REPAIR_KITS = 2;

    public RuinedCassieBlock(Properties properties) {
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
        Site site = SiteManager.at(level, pos);
        if (site == null || !site.isDirector(player)) {
            Msg.send(player, "Claim this area as your site before repairing C.A.S.S.I.E.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        if (!site.hasUpgrade(Upgrade.CASSIE_PROGRAM) && !player.isCreative()) {
            Msg.send(player, "Unlock the C.A.S.S.I.E. Program upgrade first.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative() && stack.getCount() < REPAIR_KITS) {
            Msg.send(player, "Repairing C.A.S.S.I.E. takes " + REPAIR_KITS + " Repair Kits.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative()) stack.shrink(REPAIR_KITS);
        level.setBlock(pos, ModBlocks.CASSIE.defaultBlockState().setValue(FACING, state.getValue(FACING)), 3);
        site.cassie = pos.immutable();
        site.cassieHack = 0;
        site.cassieDisabled = false;
        dev.projectredacted.site.Cassie.announce(site, "C.A.S.S.I.E. restored. Hello again, Director.");
        return InteractionResult.SUCCESS;
    }
}
