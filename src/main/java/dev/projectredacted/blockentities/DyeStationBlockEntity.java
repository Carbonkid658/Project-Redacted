package dev.projectredacted.blockentities;

import dev.projectredacted.registry.ModTags;
import dev.projectredacted.registry.ProjectRedactedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** One stack of dyeable items waiting to be dyed, and the color picked on the wheel. */
public class DyeStationBlockEntity extends BlockEntity {
    private ItemStack held = ItemStack.EMPTY;
    private int color = DyedBlockEntity.UNDYED;

    public DyeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ProjectRedactedBlockEntities.DYE_STATION, pos, state);
    }

    public static boolean dyeable(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModTags.DYEABLE);
    }

    /** The color a stack has now: its dye, or white for an undyed one. */
    public static int colorOf(ItemStack stack) {
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? DyedBlockEntity.UNDYED : dyed.rgb() & 0xFFFFFF;
    }

    public ItemStack held() {
        return held;
    }

    public int color() {
        return color;
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        setChanged();
    }

    /**
     * Puts a stack in. Whatever was in before goes back to the player unless the two stack together.
     * Returns what's left of the offered stack (the part that didn't fit).
     */
    public ItemStack insert(Player player, ItemStack stack) {
        if (!dyeable(stack)) return stack;
        if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
            int move = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
            held.grow(move);
            stack = stack.copy();
            stack.shrink(move);
            setChanged();
            return stack;
        }
        takeOut(player);
        held = stack.copy();
        setChanged();
        return ItemStack.EMPTY;
    }

    /** Gives the held stack back to the player, dropping what doesn't fit. */
    public void takeOut(Player player) {
        if (held.isEmpty()) return;
        ItemStack out = held;
        held = ItemStack.EMPTY;
        setChanged();
        if (!player.getInventory().add(out)) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), out));
        }
    }

    /** Applies the picked color to everything held. White washes the dye off. Returns how many were dyed. */
    public int dye() {
        if (held.isEmpty()) return 0;
        if (color == DyedBlockEntity.UNDYED) held.remove(DataComponents.DYED_COLOR);
        else held.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        setChanged();
        return held.getCount();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held = input.read("held", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        color = input.getIntOr("color", DyedBlockEntity.UNDYED) & 0xFFFFFF;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!held.isEmpty()) output.store("held", ItemStack.CODEC, held);
        output.putInt("color", color);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !held.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
            held = ItemStack.EMPTY;
        }
    }
}
