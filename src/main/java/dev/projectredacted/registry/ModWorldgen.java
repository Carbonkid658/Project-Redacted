package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.npc.TraderKind;
import dev.projectredacted.world.BunkerFeature;
import dev.projectredacted.world.ContactPostFeature;
import dev.projectredacted.world.RuinedSiteFeature;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** World structures: bunkers, ruined sites and faction contacts. Rarity is set in data/projectredacted/worldgen/placed_feature. */
public final class ModWorldgen {
    public static final Feature<NoneFeatureConfiguration> FOUNDATION_BUNKER = register("foundation_bunker", new BunkerFeature());
    public static final Feature<NoneFeatureConfiguration> RUINED_SITE = register("ruined_site", new RuinedSiteFeature());
    public static final Feature<NoneFeatureConfiguration> GOC_OUTPOST = register("goc_outpost", new ContactPostFeature(TraderKind.GOC_QUARTERMASTER));
    public static final Feature<NoneFeatureConfiguration> WONDERTAINMENT_STALL = register("wondertainment_stall", new ContactPostFeature(TraderKind.WONDERTAINMENT_REP));

    private static Feature<NoneFeatureConfiguration> register(String name, Feature<NoneFeatureConfiguration> feature) {
        return Registry.register(BuiltInRegistries.FEATURE, ProjectRedacted.id(name), feature);
    }

    public static void init() {
        for (String name : new String[]{"foundation_bunker", "ruined_site", "goc_outpost", "wondertainment_stall"}) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                    ResourceKey.create(Registries.PLACED_FEATURE, ProjectRedacted.id(name)));
        }
    }

    private ModWorldgen() {}
}
