package dev.projectredacted.api;

import dev.projectredacted.room.Room;
import dev.projectredacted.site.Site;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * The entry point for adding SCPs. Register a {@link ScpDefinition} per SCP from your mod initializer.
 * No SCPs are registered by this mod yet.
 */
public final class ScpApi {
    private static final Map<String, ScpDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final List<BreachListener> BREACH_LISTENERS = new ArrayList<>();

    @FunctionalInterface
    public interface BreachListener {
        /** Called when an SCP breaches its cell. {@code scp} is null for item SCPs. */
        void onBreach(ServerLevel level, Site site, Room cell, ScpDefinition definition, Entity scp);
    }

    public static ScpDefinition register(ScpDefinition definition) {
        DEFINITIONS.put(definition.id, definition);
        return definition;
    }

    public static ScpDefinition get(String id) {
        return DEFINITIONS.get(id);
    }

    public static Collection<ScpDefinition> all() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    public static ScpDefinition forEntity(EntityType<?> type) {
        for (ScpDefinition d : DEFINITIONS.values()) if (d.entityType == type) return d;
        return null;
    }

    public static ScpDefinition forItem(Item item) {
        for (ScpDefinition d : DEFINITIONS.values()) if (d.item == item) return d;
        return null;
    }

    public static int raidWeight(ScpDefinition definition) {
        return definition == null ? ObjectClass.UNCLASSIFIED.raidWeight : definition.objectClass.raidWeight;
    }

    public static void onBreach(BreachListener listener) {
        BREACH_LISTENERS.add(listener);
    }

    public static void fireBreach(ServerLevel level, Site site, Room cell, ScpDefinition definition, Entity scp) {
        for (BreachListener l : BREACH_LISTENERS) l.onBreach(level, site, cell, definition, scp);
    }

    private ScpApi() {}
}
