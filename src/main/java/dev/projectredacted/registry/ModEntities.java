package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.staff.StaffEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<StaffEntity> STAFF = register("staff",
            EntityType.Builder.of(StaffEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));
    public static final EntityType<ArmedNpcEntity> ARMED_NPC = register("armed_npc",
            EntityType.Builder.of(ArmedNpcEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));
    public static final EntityType<TraderEntity> TRADER = register("trader",
            EntityType.Builder.of(TraderEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectRedacted.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(STAFF, StaffEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ARMED_NPC, ArmedNpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createAttributes());
    }

    private ModEntities() {}
}
