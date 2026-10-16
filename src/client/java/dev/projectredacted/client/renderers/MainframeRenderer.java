package dev.projectredacted.client.renderers;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.MainframeBlockEntity;
import dev.projectredacted.client.models.MainframeGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class MainframeRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<MainframeBlockEntity, R> {

    public MainframeRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new MainframeGeoModel());
    }
}
