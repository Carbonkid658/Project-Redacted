package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.item.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
    public static final List<Item> ALL = new ArrayList<>();

    public static final Item PLASTIC = register("plastic", Item::new, new Item.Properties());
    public static final Item COIN = register("coin", Item::new, new Item.Properties());
    public static final Item DOLLAR = register("dollar", Item::new, new Item.Properties());
    public static final Item KEYCARD_1 = keycard(1);
    public static final Item KEYCARD_2 = keycard(2);
    public static final Item KEYCARD_3 = keycard(3);
    public static final Item KEYCARD_4 = keycard(4);
    public static final Item KEYCARD_5 = keycard(5);
    public static final Item CLIPBOARD = register("clipboard", ClipboardItem::new, new Item.Properties().stacksTo(1));
    public static final Item ROOM_DESIGNATOR = register("room_designator", DesignatorItem::new, new Item.Properties().stacksTo(1));
    /** Creative-only tool for framing hand-built site parts (site generation §7). */
    public static final Item PART_FRAME = register("part_frame", PartFrameItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    public static final Item FACILITY_TABLET = register("facility_tablet", TabletItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item RESEARCH_DOCUMENT = register("research_document", dev.projectredacted.item.ResearchDocumentItem::new, new Item.Properties());
    public static final Item REPAIR_KIT = register("repair_kit", RepairKitItem::new, new Item.Properties().stacksTo(16));
    public static final Item LIBRARY_TOME = register("library_tome", Item::new, new Item.Properties().rarity(Rarity.RARE));
    public static final Item LORE_DOCUMENT = register("lore_document", LoreDocumentItem::new, new Item.Properties().stacksTo(1));
    public static final Item DELIVERY_FLARE = register("delivery_flare", DeliveryFlareItem::new, new Item.Properties().stacksTo(1));
    /** Creative-only stand-in anomaly for testing containment before real SCPs exist. */
    public static final Item UNCLASSIFIED_ANOMALY = register("unclassified_anomaly", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    private static Item keycard(int level) {
        return register("keycard_" + level, p -> new KeycardItem(level, p), new Item.Properties().stacksTo(1));
    }

    public static Item keycardFor(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> KEYCARD_1;
            case 2 -> KEYCARD_2;
            case 3 -> KEYCARD_3;
            case 4 -> KEYCARD_4;
            default -> KEYCARD_5;
        };
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        Identifier id = ProjectRedacted.id(name);
        Item item = factory.apply(props.setId(ResourceKey.create(Registries.ITEM, id)));
        Registry.register(BuiltInRegistries.ITEM, id, item);
        ALL.add(item);
        return item;
    }

    public static void init() {}

    private ModItems() {}
}
