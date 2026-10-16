package dev.projectredacted.registry;

import com.mojang.serialization.Codec;
import dev.projectredacted.ProjectRedacted;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Custom item data components. */
public final class ModComponents {
    /** Which lore entry a Lore Document holds. */
    public static final DataComponentType<Integer> LORE_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ProjectRedacted.id("lore_id"),
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** What a delivery flare calls in (an SCP id registered through ScpApi). */
    public static final DataComponentType<String> DELIVERY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ProjectRedacted.id("delivery"),
            DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static void init() {}

    private ModComponents() {}
}
