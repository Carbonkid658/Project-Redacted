package dev.projectredacted.client.models;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.SiteDirectorComputerBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class SiteDirectorComputerGeoModel extends GeoModel<SiteDirectorComputerBlockEntity> {
    private static final String NAME = "site_director_computer";

    @Override
    @NotNull
    public Identifier getModelResource(@NotNull GeoRenderState renderState) {
        return Identifier.fromNamespaceAndPath("projectredacted", NAME);
    }

    @Override
    @NotNull
    public Identifier getTextureResource(@NotNull GeoRenderState renderState) {
        return Identifier.fromNamespaceAndPath("projectredacted", "textures/block/" + NAME + ".png");
    }

    @Override
    public Identifier getAnimationResource(SiteDirectorComputerBlockEntity animatable) {
        return Identifier.fromNamespaceAndPath("projectredacted", "block/" + NAME);
    }
}
