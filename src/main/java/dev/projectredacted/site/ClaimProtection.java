package dev.projectredacted.site;

import dev.projectredacted.util.Msg;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.DoorBlock;

/**
 * Keeps outsiders from building in a claim. Members build if the Director allows it. Anything already in
 * the claim (mobs, anomalies) is left alone. Raiders still break in; they're mobs, not players.
 */
public final class ClaimProtection {
    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) return true;
            Site site = SiteManager.at(level, pos);
            if (site == null || site.canBuild(player) || player.isCreative()) return true;
            Msg.actionBar(player, "This block belongs to " + site.directorName + "'s site.", ChatFormatting.RED);
            return false;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            Site site = SiteManager.at(level, hit.getBlockPos());
            if (site == null || site.canBuild(player)) return InteractionResult.PASS;
            // Outsiders may knock on doors and use keycard readers, but not place blocks or open storage.
            var state = level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof dev.projectredacted.block.KeycardReaderBlock) {
                return InteractionResult.PASS;
            }
            if (player.getItemInHand(hand).getItem() instanceof BlockItem || level.getBlockEntity(hit.getBlockPos()) != null) {
                Msg.actionBar(player, "You don't have build rights in " + site.directorName + "'s site.", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private ClaimProtection() {}
}
