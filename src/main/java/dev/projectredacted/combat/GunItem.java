package dev.projectredacted.combat;

import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A firearm players can use (NPC revamp §4.3): right-click to shoot, one ammo item per shot (none in creative). */
public class GunItem extends Item {
    public final GunType type;

    public GunItem(GunType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!player.isCreative()) {
            if (Cost.count(player, type.ammo()) <= 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
                Msg.actionBar(player, "Out of " + new ItemStack(type.ammo()).getHoverName().getString() + ".", ChatFormatting.RED);
                player.getCooldowns().addCooldown(stack, 10);
                return InteractionResult.FAIL;
            }
            Cost.take(player, type.ammo(), 1);
        }
        Guns.fire(server, player, type, player.getViewVector(1f));
        player.getCooldowns().addCooldown(stack, type.cooldown);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal((type.pellets > 1 ? type.pellets + " × " : "") + (int) type.damage + " damage · " + type.range + " block range").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Uses " + new ItemStack(type.ammo()).getHoverName().getString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
