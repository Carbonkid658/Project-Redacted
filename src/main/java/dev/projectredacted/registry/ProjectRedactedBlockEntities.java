package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.blockentities.CassieBlockEntity;
import dev.projectredacted.blockentities.ComputerBlockEntity;
import dev.projectredacted.blockentities.ConferenceTableBlockEntity;
import dev.projectredacted.blockentities.VentilationFanBlockEntity;
import dev.projectredacted.blockentities.DyeStationBlockEntity;
import dev.projectredacted.blockentities.DyedBlockEntity;
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
        public static final BlockEntityType<DyedBlockEntity> DYED_BLOCK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRedacted.id("dyed_block"),
                FabricBlockEntityTypeBuilder.create(DyedBlockEntity::new, ModBlocks.DYEABLE_BRICKS, ModBlocks.DYEABLE_FLOOR, ModBlocks.DYEABLE_TILE).build());
        public static final BlockEntityType<DyeStationBlockEntity> DYE_STATION = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRedacted.id("dye_station"),
                FabricBlockEntityTypeBuilder.create(DyeStationBlockEntity::new, ModBlocks.DYE_STATION).build());

        public static final BlockEntityType<ComputerBlockEntity> COMPUTER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRedacted.id("computer"),
                FabricBlockEntityTypeBuilder.create(ComputerBlockEntity::new, ModBlocks.DEPOT_TERMINAL, ModBlocks.OBSERVATION_CONSOLE,
                        ModBlocks.RESEARCH_TERMINAL, ModBlocks.GUARD_CONSOLE, ModBlocks.SECURITY_MONITOR).build());
        public static final BlockEntityType<ConferenceTableBlockEntity> CONFERENCE_TABLE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRedacted.id("conference_table"),
                FabricBlockEntityTypeBuilder.create(ConferenceTableBlockEntity::new, ModBlocks.CONFERENCE_TABLE).build());
        public static final BlockEntityType<VentilationFanBlockEntity> VENTILATION_FAN_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ProjectRedacted.id("ventilation_fan"),
                FabricBlockEntityTypeBuilder.create(VentilationFanBlockEntity::new, ModBlocks.VENTILATION_FAN).build());

    public static void register() {
    }
}

