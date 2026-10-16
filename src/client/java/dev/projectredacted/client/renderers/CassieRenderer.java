package dev.projectredacted.client.renderers;


import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import dev.projectredacted.blockentities.CassieBlockEntity;
import dev.projectredacted.client.models.CassieGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CassieRenderer <R extends BlockEntityRenderState & GeoRenderState>
        extends GeoBlockRenderer<CassieBlockEntity, R> {

    public CassieRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<CassieBlockEntity>
            CassieBlockEntityBlockEntityType) {
        super(context, new CassieGeoModel());
    }

    @Override
    public void captureDefaultRenderState(CassieBlockEntity animatable, Void relatedObject,
                                          R renderState, float partialTick) {
        super.captureDefaultRenderState(animatable, relatedObject, renderState, partialTick);

        BlockState state = animatable.getBlockState();
    }
}

