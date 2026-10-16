package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class ModTabs {
    public static final CreativeModeTab MAIN = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ProjectRedacted.id("main"),
            FabricCreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.MAINFRAME))
                    .title(Component.translatable("itemGroup.projectredacted.main"))
                    .displayItems((params, output) -> {
                        for (Block block : ModBlocks.ALL) {
                            // Look the item up by id, so one block with a bad item can't crash the whole inventory.
                            Item item = BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(block));
                            if (item != Items.AIR) output.accept(item);
                            else ProjectRedacted.LOG.warn("No item for block {}", BuiltInRegistries.BLOCK.getKey(block));
                        }
                        ModItems.ALL.forEach(output::accept);
                    })
                    .build());

    public static void init() {}

    private ModTabs() {}
}
