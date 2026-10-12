package dev.projectredacted.world;

import dev.projectredacted.item.LoreDocumentItem;
import dev.projectredacted.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Small helpers for building structures during world generation. */
final class GenUtil {
    static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    static void box(WorldGenLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall, BlockState inside) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    set(level, x, y, z, edge ? wall : inside);
                }
    }

    /** A chest of supplies, with coins and sometimes a lore document. */
    static void lootChest(WorldGenLevel level, BlockPos pos, RandomSource random, boolean ruin) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (!(level.getBlockEntity(pos) instanceof Container chest)) return;
        int slot = 0;
        chest.setItem(slot++, new ItemStack(ModItems.COIN, 2 + random.nextInt(ruin ? 10 : 6)));
        chest.setItem(slot++, new ItemStack(Items.BREAD, 1 + random.nextInt(4)));
        if (random.nextInt(2) == 0) chest.setItem(slot++, new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(5)));
        if (random.nextInt(3) == 0) chest.setItem(slot++, new ItemStack(ModItems.PLASTIC, 2 + random.nextInt(6)));
        if (random.nextInt(ruin ? 2 : 5) == 0) chest.setItem(slot++, LoreDocumentItem.create(ModItems.LORE_DOCUMENT, random.nextInt(LoreDocumentItem.ENTRIES)));
        if (ruin && random.nextInt(2) == 0) chest.setItem(slot++, new ItemStack(ModItems.REPAIR_KIT, 1 + random.nextInt(2)));
        if (random.nextInt(8) == 0) chest.setItem(slot++, new ItemStack(ModItems.keycardFor(1 + random.nextInt(3))));
        if (random.nextInt(10) == 0) chest.setItem(slot, new ItemStack(Items.DIAMOND));
    }

    private GenUtil() {}
}
