package dev.projectredacted.blockentities;

import dev.projectredacted.registry.ProjectRedactedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The color of a placed dyeable block. It comes from the item's {@code minecraft:dyed_color} when placed and goes
 * back onto the item when broken (see the loot tables), so the block behaves like a dyed leather item.
 * The client tints the grayscale texture with it (ProjectRedactedClient).
 */
public class DyedBlockEntity extends BlockEntity {
    /** White leaves the grayscale texture as it is. */
    public static final int UNDYED = 0xFFFFFF;

    private int color = UNDYED;

    public DyedBlockEntity(BlockPos pos, BlockState state) {
        super(ProjectRedactedBlockEntities.DYED_BLOCK, pos, state);
    }

    public int color() {
        return color;
    }

    public void setColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (rgb == color) return;
        color = rgb;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int old = color;
        color = input.getIntOr("color", UNDYED) & 0xFFFFFF;
        // the client redraws the block when a new color arrives from the server
        if (level != null && level.isClientSide() && old != color) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (color != UNDYED) output.putInt("color", color);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        DyedItemColor dyed = components.get(DataComponents.DYED_COLOR);
        color = dyed == null ? UNDYED : dyed.rgb() & 0xFFFFFF;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (color != UNDYED) builder.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("color");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
