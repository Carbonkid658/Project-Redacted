package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class ModTabs {
    public static final CreativeModeTab MAIN = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ProjectRedacted.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.MAINFRAME))
                    .title(Component.translatable("itemGroup.projectredacted.main"))
                    .displayItems((params, output) -> {
                        for (Block block : ModBlocks.ALL) output.accept(block);
                        ModItems.ALL.forEach(output::accept);
                    })
                    .build());

    public static void init() {}

    private ModTabs() {}
}
