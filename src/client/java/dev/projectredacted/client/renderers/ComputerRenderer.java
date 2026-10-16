package dev.projectredacted.client.renderers;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.ComputerBlockEntity;
import dev.projectredacted.client.models.ComputerGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class ComputerRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<ComputerBlockEntity, R> {

    public ComputerRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new ComputerGeoModel());
    }
}
