package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Tags SCP content plugs into. Add an SCP's entity or item to these and the site treats it as an anomaly. */
public final class ModTags {
    public static final TagKey<EntityType<?>> SCP_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, ProjectRedacted.id("scp"));
    public static final TagKey<Item> SCP_ITEMS = TagKey.create(Registries.ITEM, ProjectRedacted.id("scp_items"));
    /** Item-type SCPs Dr. Wondertainment's rep may stock. */
    public static final TagKey<Item> WONDERTAINMENT_SCPS = TagKey.create(Registries.ITEM, ProjectRedacted.id("wondertainment_scps"));

    /** What a Dye Station accepts: the dyeable blocks and anything else that takes dyed_color, like leather. */
    public static final TagKey<Item> DYEABLE = TagKey.create(Registries.ITEM, ProjectRedacted.id("dyeable"));

    private ModTags() {}
}
