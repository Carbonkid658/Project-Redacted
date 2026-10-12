package dev.projectredacted.block;

import com.mojang.serialization.MapCodec;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteFounding;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The heart of a site. Placing one founds the player's site; while it is offline the site stops running. */
public class MainframeBlock extends FacingBlock {
    public static final MapCodec<MainframeBlock> CODEC = simpleCodec(MainframeBlock::new);

    public MainframeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            SiteFounding.onMainframePlaced(serverLevel, pos, player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.REPAIR_KIT)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.byMainframe(pos);
        if (site == null) return InteractionResult.PASS;
        if (site.mainframeSabotaged) {
            site.mainframeSabotaged = false;
            if (!player.isCreative()) stack.shrink(1);
            site.log("Mainframe repaired by " + player.getName().getString() + ".");
            Msg.send(player, "The Mainframe is back online.", ChatFormatting.GREEN);
        } else {
            Msg.send(player, "The Mainframe doesn't need repairs.", ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.byMainframe(pos);
        if (site == null) {
            Msg.send(player, "This Mainframe isn't linked to a site.", ChatFormatting.GRAY);
        } else {
            Msg.send(player, "Mainframe of " + site.directorName + "'s site: " + (site.mainframeOnline() ? "ONLINE" : "OFFLINE")
                    + ". Use a Site Director's Computer to manage the site.", site.mainframeOnline() ? ChatFormatting.GREEN : ChatFormatting.RED);
        }
        return InteractionResult.SUCCESS;
    }
}
