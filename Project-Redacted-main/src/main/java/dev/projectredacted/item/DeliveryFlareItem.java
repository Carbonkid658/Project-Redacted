package dev.projectredacted.item;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.registry.ModComponents;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Marshall, Carter & Dark deliver what you bought wherever you light the flare. */
public class DeliveryFlareItem extends Item {
    public DeliveryFlareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) return InteractionResult.SUCCESS;
        String scpId = context.getItemInHand().get(ModComponents.DELIVERY);
        ScpDefinition def = scpId == null ? null : ScpApi.get(scpId);
        if (def == null) {
            Msg.send(context.getPlayer(), "The flare fizzles. Nothing is coming.", ChatFormatting.GRAY);
            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().above();
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.3, 1.5, 0.3, 0.02);
        def.deliver(level, pos);
        Msg.send(context.getPlayer(), "Marshall, Carter & Dark thank you for your business.", ChatFormatting.GOLD);
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
