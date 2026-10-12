package dev.projectredacted.item;

import dev.projectredacted.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Documents found in abandoned structures that tell how the world ended up this way. */
public class LoreDocumentItem extends Item {
    public static final int ENTRIES = 8;

    public LoreDocumentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, int loreId) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.LORE_ID, Math.floorMod(loreId, ENTRIES));
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int id = player.getItemInHand(hand).getOrDefault(ModComponents.LORE_ID, 0);
        player.sendSystemMessage(Component.translatable("lore.projectredacted." + id + ".title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("lore.projectredacted." + id + ".body").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }
}
