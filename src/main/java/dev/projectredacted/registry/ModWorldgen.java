package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.world.BunkerFeature;
import dev.projectredacted.world.ContactPostFeature;
import dev.projectredacted.world.RuinPiece;
import dev.projectredacted.world.RuinedSiteStructure;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;

/**
 * World structures: bunkers, ruined sites and faction contacts. Each feature type is registered as a codec here;
 * the feature instances and their rarity live in data/projectredacted/worldgen/feature and placed_feature. Ruined
 * sites are a structure built from site parts, set up in worldgen/structure and structure_set.
 */
public final class ModWorldgen {
    static {
        register("foundation_bunker", BunkerFeature.CODEC);
        RuinedSiteStructure.TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ProjectRedacted.id("ruined_site"), () -> RuinedSiteStructure.CODEC);
        RuinPiece.TYPE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ProjectRedacted.id("ruin_part"), RuinPiece::new);
        register("goc_outpost", ContactPostFeature.GOC_CODEC);
        register("wondertainment_stall", ContactPostFeature.WONDERTAINMENT_CODEC);
    }

    private static void register(String name, MapCodec<? extends Feature> codec) {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ProjectRedacted.id(name), codec);
    }

    public static void init() {
        // Ruined sites are a structure (worldgen/structure and structure_set), not a feature.
        for (String name : new String[]{"foundation_bunker", "goc_outpost", "wondertainment_stall"}) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                    ResourceKey.create(Registries.PLACED_FEATURE, ProjectRedacted.id(name)));
        }
    }

    private ModWorldgen() {}
}
