package dev.projectredacted.client.renderers;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.VentilationFanBlockEntity;
import dev.projectredacted.client.models.VentilationFanGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class VentilationFanRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<VentilationFanBlockEntity, R> {

    public VentilationFanRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new VentilationFanGeoModel());
    }

    /** The model reaches past its own block, so keep drawing it while that block is off screen. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
