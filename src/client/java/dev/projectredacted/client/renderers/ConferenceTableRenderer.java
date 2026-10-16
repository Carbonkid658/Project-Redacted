package dev.projectredacted.client.renderers;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.ConferenceTableBlockEntity;
import dev.projectredacted.client.models.ConferenceTableGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class ConferenceTableRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<ConferenceTableBlockEntity, R> {

    public ConferenceTableRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new ConferenceTableGeoModel());
    }

    /** The model reaches past its own block, so keep drawing it while that block is off screen. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
