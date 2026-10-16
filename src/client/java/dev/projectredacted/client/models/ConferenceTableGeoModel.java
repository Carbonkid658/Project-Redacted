package dev.projectredacted.client.models;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.ConferenceTableBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class ConferenceTableGeoModel extends GeoModel<ConferenceTableBlockEntity> {
    private static final String NAME = "conference_table";

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
    public Identifier getAnimationResource(ConferenceTableBlockEntity animatable) {
        return Identifier.fromNamespaceAndPath("projectredacted", NAME);
    }
}
