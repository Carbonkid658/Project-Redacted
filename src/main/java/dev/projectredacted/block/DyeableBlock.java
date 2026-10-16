package dev.projectredacted.block;

import dev.projectredacted.blockentities.DyedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A grayscale block that takes any color, like dyed leather: dye it at a Dye Station (or in a crafting grid with
 * dyes) and the placed block shows that color. The color lives in a {@link DyedBlockEntity}.
 */
public class DyeableBlock extends Block implements EntityBlock {
    public DyeableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DyedBlockEntity(pos, state);
    }

    /** Pick block gives the same color. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        if (level.getBlockEntity(pos) instanceof DyedBlockEntity dyed && dyed.color() != DyedBlockEntity.UNDYED) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyed.color()));
        }
        return stack;
    }
}
