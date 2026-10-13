package dev.projectredacted.site.gen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.site.SiteType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Site types as arrangement rules (design §4): where storeys go and how the facility meets the surface. The
 * numbers can be tuned with data/&lt;ns&gt;/projectredacted/site_types/&lt;type&gt;.json.
 */
public class SiteTypeRules {
    /** How far below the surface the Director's Office storey sits (Underground). */
    public int depth = 20;
    /** Share of the claim a storey's parts may cover before growth moves to the next storey. */
    public double crowded = 0.45;
    /** Storeys above (Surface-Tall) or below (Underground) the first one that growth may use; -1 for no limit. */
    public int maxStoreys = -1;

    private static final Map<SiteType, SiteTypeRules> RULES = new EnumMap<>(SiteType.class);
    private static final Gson GSON = new Gson();

    public static SiteTypeRules of(SiteType type) {
        return RULES.computeIfAbsent(type, t -> new SiteTypeRules());
    }

    /** Storeys between the office storey and the surface storey (Underground only). */
    public static int surfaceStorey(SiteType type) {
        if (type != SiteType.UNDERGROUND) return 0;
        return Math.max(1, Math.round(of(type).depth / (float) SitePart.STOREY));
    }

    /** Whether a part of this kind may sit on this storey (0 = the Director's Office storey). */
    public static boolean allowed(SiteType type, SitePart part, int storey, int storeys) {
        int top = storey + storeys - 1;
        int surface = surfaceStorey(type);
        int limit = of(type).maxStoreys;
        return switch (type) {
            case SURFACE_FLAT -> storey == 0 && top == 0;
            case SURFACE_TALL -> storey >= 0 && (part.kind != SitePart.Kind.ENTRANCE || storey == 0) && (limit < 0 || top <= limit);
            case UNDERGROUND -> {
                if (part.kind == SitePart.Kind.ENTRANCE) yield storey == surface && top == surface;
                if (part.kind == SitePart.Kind.STAIRWELL || part.kind == SitePart.Kind.HALLWAY) yield top <= surface && (limit < 0 || storey >= -limit);
                yield top < surface && (limit < 0 || storey >= -limit);
            }
        };
    }

    public static void reload(ResourceManager manager) {
        RULES.clear();
        Map<Identifier, Resource> found = manager.listResources("projectredacted/site_types", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String path = e.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5).toUpperCase(Locale.ROOT).replace('-', '_');
            SiteType type;
            try {
                type = SiteType.valueOf(name);
            } catch (IllegalArgumentException ex) {
                ProjectRedacted.LOG.warn("Unknown site type file {}", e.getKey());
                continue;
            }
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject o = GSON.fromJson(reader, JsonObject.class);
                SiteTypeRules r = new SiteTypeRules();
                if (o.has("depth")) r.depth = o.get("depth").getAsInt();
                if (o.has("crowded")) r.crowded = o.get("crowded").getAsDouble();
                if (o.has("max_storeys")) r.maxStoreys = o.get("max_storeys").getAsInt();
                RULES.put(type, r);
            } catch (Exception ex) {
                ProjectRedacted.LOG.warn("Couldn't read site type rules {}: {}", e.getKey(), ex.getMessage());
            }
        }
    }
}
