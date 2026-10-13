package dev.projectredacted.client;

import dev.projectredacted.ProjectRedacted;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/** Draws staff, raiders and traders as people, with a skin picked by role or faction. */
public class NpcRenderer<T extends Mob> extends HumanoidMobRenderer<T, NpcRenderState, HumanoidModel<NpcRenderState>> {
    private final String folder;
    private final Function<T, String> skin;

    public NpcRenderer(EntityRendererProvider.Context context, String folder, Function<T, String> skin) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.folder = folder;
        this.skin = skin;
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    @Override
    public void extractRenderState(T entity, NpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skin = skin.apply(entity);
    }

    @Override
    public Identifier getTextureLocation(NpcRenderState state) {
        return ProjectRedacted.id("textures/entity/" + folder + "/" + state.skin + ".png");
    }
}
