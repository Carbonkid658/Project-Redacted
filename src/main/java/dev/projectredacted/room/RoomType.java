package dev.projectredacted.room;

import com.google.gson.annotations.SerializedName;
import dev.projectredacted.staff.StaffRole;
import dev.projectredacted.util.Cost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A kind of room. Built-in types live in {@link RoomTypes}; datapacks and addons add more as JSON files in
 * {@code data/<namespace>/projectredacted/rooms/<name>.json} using the same field names.
 */
public class RoomType {
    public String id;
    public String name;
    public String category = "Custom";
    public String description = "";
    /** Builder layout to use: one of the built-in styles, or "plain" for a furnished-less room. */
    public String style = "plain";
    /** Optional structure template (namespace:path) placed once the room's shell is built. */
    public String structure;
    public int minSide = 5, maxSide = 32, minHeight = 4, maxHeight = 10;
    public int powerDraw = 1;
    public int waterDraw = 0;
    /** Staff role that must work at the site before this room unlocks (null for none). */
    public String requiredRole;
    public int requiredLevel = 0;
    /** Only one of these per site (the Supply Depot). */
    public boolean unique;
    /** Keycard level for the room's door reader (0 means a plain door). */
    public int clearance = 0;
    public String icon = "minecraft:bricks";
    /** Area kind (v4 §2), e.g. "living"; null to use the area lists. */
    public String area;
    public Map<String, Integer> cost = new LinkedHashMap<>();
    /**
     * The block that makes a closed space this room (v4 §5.8), e.g. "projectredacted:bunk_bed". Datapack rooms
     * name it as "key_block" and can use any block. Null for a room type without one.
     */
    @SerializedName("key_block")
    public String keyBlock;

    public transient boolean builtIn;

    public StaffRole role() {
        if (requiredRole == null || requiredRole.isEmpty()) return null;
        try {
            return StaffRole.valueOf(requiredRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Cost cost() {
        return Cost.parse(cost);
    }

    /** The key block, or null when there's none (or it isn't registered). */
    public net.minecraft.world.level.block.Block keyBlockDef() {
        Identifier rl = keyBlock == null ? null : Identifier.tryParse(keyBlock);
        if (rl == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
    }

    public Item iconItem() {
        Identifier rl = Identifier.tryParse(icon);
        return rl == null ? Items.BRICKS : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.BRICKS);
    }

    public boolean isContainment() {
        return "Containment".equals(category);
    }

    public boolean isHousing() {
        return "Housing".equals(category);
    }

    public boolean isCheckpoint() {
        return "Checkpoint".equals(category);
    }
}
