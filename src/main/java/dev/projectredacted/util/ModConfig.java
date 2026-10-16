package dev.projectredacted.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.projectredacted.ProjectRedacted;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server settings in config/projectredacted.json. Missing values get their defaults and are written back. */
public final class ModConfig {
    /**
     * How far room detection searches around a key block or Area Hall Marker (v4 §5.8, V22): a cube this many
     * blocks across. A space that isn't closed within it doesn't become a room.
     */
    public int roomScanSize = 50;

    private static ModConfig current = new ModConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ModConfig get() {
        return current;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(ProjectRedacted.MOD_ID + ".json");
        ModConfig c = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                c = GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                ProjectRedacted.LOG.warn("Couldn't read {}: {}", file, e.getMessage());
            }
        }
        if (c == null) c = new ModConfig();
        c.roomScanSize = Math.max(5, Math.min(256, c.roomScanSize));
        current = c;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            ProjectRedacted.LOG.warn("Couldn't write {}: {}", file, e.getMessage());
        }
    }

    private ModConfig() {}
}
