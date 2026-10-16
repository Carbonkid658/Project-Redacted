package dev.projectredacted.client.renderers;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.SiteDirectorComputerBlockEntity;
import dev.projectredacted.client.models.SiteDirectorComputerGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class SiteDirectorComputerRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<SiteDirectorComputerBlockEntity, R> {

    public SiteDirectorComputerRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new SiteDirectorComputerGeoModel());
    }
}
