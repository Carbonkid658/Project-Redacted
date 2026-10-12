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
import net.minecraft.world.level.block.SoundType;
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
    public static final Block FACILITY_FLOOR = register("facility_floor", Block::new,
            props().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.5f, 8f).requiresCorrectToolForDrops());
    /** Heavy cells are built from this. Blast resistant. */
    public static final Block CONTAINMENT_CONCRETE = register("containment_concrete", Block::new,
            props().mapColor(MapColor.COLOR_GRAY).strength(25f, 1200f).requiresCorrectToolForDrops());
    public static final Block REINFORCED_GLASS = register("reinforced_glass", TransparentBlock::new,
            props().mapColor(MapColor.NONE).strength(8f, 600f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final Block REINFORCED_DOOR = registerDoor("reinforced_door",
            props().mapColor(MapColor.METAL).strength(15f, 600f).requiresCorrectToolForDrops().noOcclusion());

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

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties props) {
        Identifier id = ProjectRedacted.id(name);
        T block = factory.apply(props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }

    private static Block registerDoor(String name, BlockBehaviour.Properties props) {
        Identifier id = ProjectRedacted.id(name);
        Block block = new DoorBlock(BlockSetType.IRON, props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new DoubleHighBlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }

    public static void init() {}

    private ModBlocks() {}
}
