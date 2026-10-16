package dev.projectredacted.client.models;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.CassieBlockEntity;
import dev.projectredacted.client.renderers.CassieRenderer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class CassieGeoModel extends GeoModel<CassieBlockEntity> {
    @Override
    @NotNull
    public Identifier getModelResource(
            @NotNull GeoRenderState renderState) {
        return Identifier.fromNamespaceAndPath("projectredacted", "cassie");
    }

    @Override
    @NotNull
    public Identifier getTextureResource(
            @NotNull GeoRenderState renderState) {
        return Identifier.fromNamespaceAndPath("projectredacted", "textures/block/cassie.png");
    }

    @Override
    public Identifier getAnimationResource(CassieBlockEntity cassieBlockEntity) {
        @NotNull CassieBlockEntity animatable; {
            return Identifier.fromNamespaceAndPath("projectredacted", "block/cassie");
    }
}}
