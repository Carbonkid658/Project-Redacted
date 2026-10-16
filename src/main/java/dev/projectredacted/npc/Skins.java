package dev.projectredacted.npc;

import dev.projectredacted.ProjectRedacted;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * NPC skin pools (NPC revamp §4.1): {@code textures/entity/<group>/<pool>/*.png} in the vanilla 64×64 player
 * layout, with optional {@code rank<N>/} folders for staff ranks. An NPC picks a skin when it spawns and keeps
 * it; the pick is the file name ("researcher_2_slim", or "rank3/captain_a"), so adding skins never changes
 * existing NPCs. A name ending in "_slim" uses slim arms.
 */
public final class Skins {
    public static final String STAFF = "staff", FACTION = "faction", TRADER = "trader";

    /**
     * Skins the client found in its resource packs, for the server running in the same game (single player):
     * group, pool → names. Set by the client; null on a dedicated server.
     */
    public static volatile BiFunction<String, String, List<String>> clientPool;

    private static final Map<String, List<String>> JAR = new HashMap<>();

    /** Every skin in a pool: the mod's own, plus resource-pack ones the local client found. Sorted. */
    public static List<String> pool(String group, String pool) {
        TreeSet<String> out = new TreeSet<>(jar(group, pool));
        var client = clientPool;
        if (client != null) {
            List<String> extra = client.apply(group, pool);
            if (extra != null) out.addAll(extra);
        }
        return new ArrayList<>(out);
    }

    /** A random skin from the pool; for staff, from the rank folder when it has any (§4.1). Empty if the pool is empty. */
    public static String pick(String group, String pool, int rank, Random random) {
        List<String> all = pool(group, pool), ranked = new ArrayList<>(), plain = new ArrayList<>();
        String prefix = "rank" + rank + "/";
        for (String s : all) {
            if (s.startsWith(prefix)) ranked.add(s);
            else if (!s.contains("/")) plain.add(s);
        }
        List<String> from = !ranked.isEmpty() ? ranked : plain;
        return from.isEmpty() ? "" : from.get(random.nextInt(from.size()));
    }

    public static boolean slim(String skin) {
        return skin != null && skin.endsWith("_slim");
    }

    /** The texture, as "projectredacted:textures/entity/staff/researcher/researcher_1.png". */
    public static String texture(String group, String pool, String skin) {
        if (skin == null || skin.isEmpty()) {
            List<String> all = pool(group, pool);
            if (all.isEmpty()) return "minecraft:textures/entity/player/wide/steve.png";
            skin = all.get(0);
        }
        return ProjectRedacted.MOD_ID + ":textures/entity/" + group + "/" + pool + "/" + skin + ".png";
    }

    /** The mod's own skins, listed from its jar (works on servers too). */
    private static synchronized List<String> jar(String group, String pool) {
        return JAR.computeIfAbsent(group + "/" + pool, k -> {
            List<String> out = new ArrayList<>();
            try {
                var mod = FabricLoader.getInstance().getModContainer(ProjectRedacted.MOD_ID);
                if (mod.isEmpty()) return out;
                Optional<Path> dir = mod.get().findPath("assets/" + ProjectRedacted.MOD_ID + "/textures/entity/" + group + "/" + pool);
                if (dir.isEmpty() || !Files.isDirectory(dir.get())) return out;
                Path root = dir.get();
                try (Stream<Path> files = Files.walk(root, 2)) {
                    files.filter(f -> f.toString().endsWith(".png")).forEach(f -> {
                        String rel = root.relativize(f).toString().replace('\\', '/');
                        out.add(rel.substring(0, rel.length() - 4));
                    });
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(Skins.class).warn("Couldn't list NPC skins in {}: {}", k, e.toString());
            }
            Collections.sort(out);
            return out;
        });
    }

    private Skins() {}
}
