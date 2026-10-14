package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.blockentities.CassieBlockEntity;
import dev.projectredacted.blockentities.MainframeBlockEntity;
import dev.projectredacted.blockentities.SiteDirectorComputerBlockEntity;
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
                                ModBlocks.CASSIE,
                                ModBlocks.RUINED_CASSIE
                        ).build()
                );
        public static final BlockEntityType<MainframeBlockEntity> MAINFRAME_BLOCK_ENTITY =
                Registry.register(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE,
                        Identifier.fromNamespaceAndPath(ProjectRedacted.MOD_ID, "mainframe_block_entity"),
                        FabricBlockEntityTypeBuilder.create(MainframeBlockEntity::new, ModBlocks.MAINFRAME).build()
                );
        public static final BlockEntityType<SiteDirectorComputerBlockEntity> SITE_DIRECTOR_COMPUTER_BLOCK_ENTITY =
                Registry.register(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE,
                        Identifier.fromNamespaceAndPath(ProjectRedacted.MOD_ID, "site_director_computer_block_entity"),
                        FabricBlockEntityTypeBuilder.create(SiteDirectorComputerBlockEntity::new, ModBlocks.SITE_DIRECTOR_COMPUTER).build()
                );
    public static void register() {
    }
}

