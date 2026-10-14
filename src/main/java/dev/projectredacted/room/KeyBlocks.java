package dev.projectredacted.room;

import dev.projectredacted.api.ScpApi;
import dev.projectredacted.api.ScpDefinition;
import dev.projectredacted.site.gen.PartKit;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which room a key block makes (site generation v4 §5.8): every room type names its key block, and an SCP's
 * containment unit makes that SCP's containment room.
 */
public final class KeyBlocks {
    /** A key block's room: its type, and for an SCP's containment unit the SCP (as in {@link PartKit#scpKey}). */
    public record Key(String roomType, String scp) {}

    public static Key of(BlockState state) {
        return state == null ? null : of(state.getBlock());
    }

    private static final java.util.Map<Block, Key> CACHE = new java.util.HashMap<>();
    private static int cachedScps = -1;

    public static Key of(Block block) {
        if (block == null) return null;
        if (cachedScps != ScpApi.all().size()) reset();
        if (CACHE.isEmpty()) {
            for (RoomType t : RoomTypes.all()) {
                Block b = t.keyBlockDef();
                if (b != null) CACHE.putIfAbsent(b, new Key(t.id, null));
            }
            for (ScpDefinition def : ScpApi.all()) {
                if (def.keyBlock == null) continue;
                String scp = PartKit.scpKey(def);
                CACHE.putIfAbsent(def.keyBlock, new Key(PartKit.cellFor(scp), scp));
            }
            cachedScps = ScpApi.all().size();
        }
        return CACHE.get(block);
    }

    /** Forgets the lookup after room types are reloaded. */
    public static void reset() {
        CACHE.clear();
        cachedScps = -1;
    }

    public static boolean isKey(BlockState state) {
        return of(state) != null;
    }

    /** Key blocks that stand for one person's bed: they count toward housing like real beds (GD §3). */
    public static boolean isBed(BlockState state) {
        return state.getBlock() instanceof dev.projectredacted.block.KeyBlock k && k.bed;
    }

    /** The key block a room type needs, for messages: "Bunk Bed". */
    public static String name(RoomType t) {
        Block b = t == null ? null : t.keyBlockDef();
        return b == null ? null : b.getName().getString();
    }

    private KeyBlocks() {}
}
