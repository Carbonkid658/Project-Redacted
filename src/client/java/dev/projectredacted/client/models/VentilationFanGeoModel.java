package dev.projectredacted.client.models;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.VentilationFanBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class VentilationFanGeoModel extends GeoModel<VentilationFanBlockEntity> {
    private static final String NAME = "ventilation_fan";

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
    public Identifier getAnimationResource(VentilationFanBlockEntity animatable) {
        return Identifier.fromNamespaceAndPath("projectredacted", NAME);
    }
}
