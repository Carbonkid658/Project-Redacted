package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.blockentities.CassieBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ProjectRedactedBlockEntities {
        public static final BlockEntityType<CassieBlockEntity> CASSIE_BLOCK_ENTITY =
                Registry.register(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE,
                        Identifier.fromNamespaceAndPath(ProjectRedacted.MOD_ID, "cassie_block_entity"),
                        FabricBlockEntityTypeBuilder.create(
                                CassieBlockEntity::new,
                                ModBlocks.CASSIE
                        ).build()
                );
    public static void register() {
    }
}

