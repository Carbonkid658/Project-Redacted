package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.block.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    // Materials and decoration
    public static final Block PLASTIC_BLOCK = register("plastic_block", Block::new,
            props().mapColor(MapColor.SNOW).strength(1.5f, 4f).sound(SoundType.BAMBOO_WOOD));

    public static final Block CEILING_LIGHT = register("ceiling_light", Block::new,
            props().mapColor(MapColor.SNOW).strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> 15));
    public static final Block FACILITY_WALL = register("facility_wall", Block::new,
            props().mapColor(MapColor.QUARTZ).strength(2.5f, 8f).requiresCorrectToolForDrops());
    public static final Block FACILITY_TILE = register("facility_tile", Block::new,
            props().mapColor(MapColor.QUARTZ).strength(2.5f, 8f).requiresCorrectToolForDrops());
    public static final Block FACILITY_FLOOR = register("facility_floor", Block::new,
            props().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.5f, 8f).requiresCorrectToolForDrops());
    // Stairs and slabs of the facility blocks.
    public static final Block FACILITY_WALL_STAIRS = register("facility_wall_stairs", p -> new StairBlock(FACILITY_WALL.defaultBlockState(), p), facilityProps(MapColor.QUARTZ));
    public static final Block FACILITY_WALL_SLAB = register("facility_wall_slab", SlabBlock::new, facilityProps(MapColor.QUARTZ));
    public static final Block FACILITY_TILE_STAIRS = register("facility_tile_stairs", p -> new StairBlock(FACILITY_TILE.defaultBlockState(), p), facilityProps(MapColor.QUARTZ));
    public static final Block FACILITY_TILE_SLAB = register("facility_tile_slab", SlabBlock::new, facilityProps(MapColor.QUARTZ));
    public static final Block FACILITY_FLOOR_STAIRS = register("facility_floor_stairs", p -> new StairBlock(FACILITY_FLOOR.defaultBlockState(), p), facilityProps(MapColor.COLOR_LIGHT_GRAY));
    public static final Block FACILITY_FLOOR_SLAB = register("facility_floor_slab", SlabBlock::new, facilityProps(MapColor.COLOR_LIGHT_GRAY));
    // Coloured tiles, with stairs and slabs.
    public static final Block BLUE_TILE = register("blue_tile", Block::new, facilityProps(MapColor.COLOR_BLUE));
    public static final Block BLUE_TILE_STAIRS = register("blue_tile_stairs", p -> new StairBlock(BLUE_TILE.defaultBlockState(), p), facilityProps(MapColor.COLOR_BLUE));
    public static final Block BLUE_TILE_SLAB = register("blue_tile_slab", SlabBlock::new, facilityProps(MapColor.COLOR_BLUE));
    public static final Block SECURITY_TILE = register("security_tile", Block::new, facilityProps(MapColor.COLOR_GRAY));
    public static final Block SECURITY_TILE_STAIRS = register("security_tile_stairs", p -> new StairBlock(SECURITY_TILE.defaultBlockState(), p), facilityProps(MapColor.COLOR_GRAY));
    public static final Block SECURITY_TILE_SLAB = register("security_tile_slab", SlabBlock::new, facilityProps(MapColor.COLOR_GRAY));
    public static final Block OFFICE_TILE = register("office_tile", Block::new, facilityProps(MapColor.COLOR_LIGHT_GRAY));
    public static final Block OFFICE_TILE_STAIRS = register("office_tile_stairs", p -> new StairBlock(OFFICE_TILE.defaultBlockState(), p), facilityProps(MapColor.COLOR_LIGHT_GRAY));
    public static final Block OFFICE_TILE_SLAB = register("office_tile_slab", SlabBlock::new, facilityProps(MapColor.COLOR_LIGHT_GRAY));
    public static final Block SAGE_GREEN_TILE = register("sage_green_tile", Block::new, facilityProps(MapColor.COLOR_GREEN));
    public static final Block SAGE_GREEN_TILE_STAIRS = register("sage_green_tile_stairs", p -> new StairBlock(SAGE_GREEN_TILE.defaultBlockState(), p), facilityProps(MapColor.COLOR_GREEN));
    public static final Block SAGE_GREEN_TILE_SLAB = register("sage_green_tile_slab", SlabBlock::new, facilityProps(MapColor.COLOR_GREEN));
    /** Heavy cells are built from this. Blast resistant. */
    public static final Block CONTAINMENT_CONCRETE = register("containment_concrete", Block::new,
            props().mapColor(MapColor.COLOR_GRAY).strength(25f, 1200f).requiresCorrectToolForDrops());
    public static final Block REINFORCED_GLASS = register("reinforced_glass", TransparentBlock::new,
            props().mapColor(MapColor.NONE).strength(8f, 600f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> false));
    public static final Block REINFORCED_DOOR = registerDoor("reinforced_door", p -> new DoorBlock(BlockSetType.IRON, p),
            props().mapColor(MapColor.METAL).strength(15f, 600f).requiresCorrectToolForDrops().noOcclusion());
    /** Slides open for redstone and keycard readers, like an iron door. */
    public static final Block SLIDING_DOOR = registerDoor("sliding_door", SlidingDoorBlock::new,
            props().mapColor(MapColor.METAL).strength(5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    /** Invisible; marks where NPCs enter a room and where generated rooms join a hall (v4 §5.4). Creative only. */
    public static final Block ROOM_ENTRANCE_MARKER = register("room_entrance_marker", RoomEntranceMarkerBlock::new,
            props().strength(-1f, 3600000f).noCollision().noLootTable().noOcclusion().noTerrainParticles().isValidSpawn((s, l, p, e) -> false));

    /** Invisible; makes a hand-built hallway part of an area so auto-place can attach rooms to it (v4 §5.8, V25). */
    public static final Block AREA_HALL_MARKER = register("area_hall_marker", AreaHallMarkerBlock::new,
            props().strength(0.3f).noCollision().noOcclusion().noTerrainParticles().isValidSpawn((s, l, p, e) -> false));

    // Key blocks (v4 §5.8): a closed space holding one becomes that room. Placeholder art for now.
    public static final Block RECEPTION_DESK = register("reception_desk", p -> new KeyBlock(p, false), keyProps(MapColor.WOOD));
    public static final Block WEAPON_RACK = register("weapon_rack", p -> new KeyBlock(p, false), keyProps(MapColor.WOOD));
    public static final Block PRISONER_BUNK = register("prisoner_bunk", p -> new KeyBlock(p, true), keyProps(MapColor.COLOR_LIGHT_GRAY));
    public static final Block CHECKPOINT_DESK = register("checkpoint_desk", p -> new KeyBlock(p, false), keyProps(MapColor.METAL));
    public static final Block BLAST_DOOR_CONTROLLER = register("blast_door_controller", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_YELLOW));
    public static final Block EMERGENCY_HATCH = register("emergency_hatch", p -> new KeyBlock(p, false), keyProps(MapColor.METAL));
    public static final Block DOCK_CONTROL_PANEL = register("dock_control_panel", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_ORANGE));
    public static final Block FILING_CABINET = register("filing_cabinet", p -> new KeyBlock(p, false), keyProps(MapColor.METAL));
    public static final Block BUNK_BED = register("bunk_bed", p -> new KeyBlock(p, true), keyProps(MapColor.COLOR_RED));
    public static final Block PERSONAL_LOCKER = register("personal_locker", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_BLUE));
    public static final Block SERVING_COUNTER = register("serving_counter", p -> new KeyBlock(p, false), keyProps(MapColor.QUARTZ));
    public static final Block INDUSTRIAL_STOVE = register("industrial_stove", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_GRAY));
    public static final Block ARCADE_CABINET = register("arcade_cabinet", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_PURPLE));
    public static final Block SHOWER_HEAD = register("shower_head", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_CYAN));
    public static final Block HOSPITAL_BED = register("hospital_bed", p -> new KeyBlock(p, true), keyProps(MapColor.SNOW));
    public static final Block EXAMINATION_TABLE = register("examination_table", p -> new KeyBlock(p, false), keyProps(MapColor.SNOW));
    public static final Block TICKET_DISPENSER = register("ticket_dispenser", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_RED));
    public static final Block LAB_BENCH = register("lab_bench", p -> new KeyBlock(p, false), keyProps(MapColor.SNOW));
    public static final Block RESEARCH_TERMINAL = register("research_terminal", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_LIGHT_BLUE));
    public static final Block CELL_CONTROL_PANEL_LIGHT = register("cell_control_panel_light", p -> new KeyBlock(p, false), keyProps(MapColor.STONE));
    public static final Block CELL_CONTROL_PANEL_MEDIUM = register("cell_control_panel_medium", p -> new KeyBlock(p, false), keyProps(MapColor.TERRACOTTA_CYAN));
    public static final Block CELL_CONTROL_PANEL_HEAVY = register("cell_control_panel_heavy", p -> new KeyBlock(p, false), keyProps(MapColor.COLOR_GRAY));
    public static final Block OBSERVATION_CONSOLE = register("observation_console", p -> new KeyBlock(p, false), keyProps(MapColor.METAL));
    public static final Block SUPPLY_SHELF = register("supply_shelf", p -> new KeyBlock(p, false), keyProps(MapColor.WOOD));
    public static final Block DEPOT_TERMINAL = register("depot_terminal", p -> new KeyBlock(p, false), keyProps(MapColor.METAL));

    // Site machines
    public static final Block MAINFRAME = register("mainframe", MainframeBlock::new,
            props().mapColor(MapColor.COLOR_BLACK).strength(5f, 12f).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 6));
    public static final Block RUINED_MAINFRAME = register("ruined_mainframe", RuinedMainframeBlock::new,
            props().mapColor(MapColor.COLOR_BLACK).strength(5f, 12f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final Block SITE_DIRECTOR_COMPUTER = register("site_director_computer", TerminalBlock::new,
            props().mapColor(MapColor.SNOW).strength(2f, 6f).sound(SoundType.METAL).lightLevel(s -> 4));
    public static final Block CASSIE = register("cassie", CassieBlock::new,
            props().mapColor(MapColor.COLOR_GRAY).strength(6f, 30f).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 8));
    public static final Block RUINED_CASSIE = register("ruined_cassie", RuinedCassieBlock::new,
            props().mapColor(MapColor.COLOR_GRAY).strength(6f, 30f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final Block CIRCUIT_BREAKER = register("circuit_breaker", CircuitBreakerBlock::new,
            props().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final Block GENERATOR_CORE = register("generator_core", FacingBlock::new,
            props().mapColor(MapColor.COLOR_YELLOW).strength(4f, 8f).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 10));
    public static final Block WATER_PUMP = register("water_pump", FacingBlock::new,
            props().mapColor(MapColor.WATER).strength(4f, 8f).sound(SoundType.COPPER).requiresCorrectToolForDrops());
    public static final Block KEYCARD_READER = register("keycard_reader", KeycardReaderBlock::new,
            props().mapColor(MapColor.METAL).strength(4f, 12f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final Block ALPHA_WARHEAD = register("alpha_warhead", AlphaWarheadBlock::new,
            props().mapColor(MapColor.COLOR_RED).strength(50f, 1200f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of();
    }

    /** Key blocks: furniture-strength, mined with a pickaxe. */
    private static BlockBehaviour.Properties keyProps(MapColor color) {
        return props().mapColor(color).strength(2.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** Same strength as the facility blocks themselves. */
    private static BlockBehaviour.Properties facilityProps(MapColor color) {
        return props().mapColor(color).strength(2.5f, 8f).requiresCorrectToolForDrops();
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
        Identifier id = ProjectRedacted.id(name);
        T block = factory.apply(props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }

    private static Block registerDoor(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        Identifier id = ProjectRedacted.id(name);
        Block block = factory.apply(props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new DoubleHighBlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }

    public static void init() {}

    private ModBlocks() {}
}
