package dev.projectredacted.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.npc.Skins;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

import java.util.*;
import java.util.function.Function;

/**
 * Draws staff, raiders and traders as people (NPC revamp §4.1). Skins are vanilla 64×64 player skins from the
 * pool folder {@code textures/entity/<group>/<pool>/}; a skin whose name ends in "_slim" gets slim arms.
 */
public class NpcRenderer<T extends Mob> extends HumanoidMobRenderer<T, NpcRenderState, HumanoidModel<NpcRenderState>> {
    /** Skins the resource packs hold, per group and pool, listed when renderers are (re)built on a resource reload. */
    private static final Map<String, Map<String, List<String>>> POOLS = new HashMap<>();
    private static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private final String group;
    private final Function<T, String> pool, skin;
    private final HumanoidModel<NpcRenderState> wide, slim;

    public NpcRenderer(EntityRendererProvider.Context context, String group, Function<T, String> pool, Function<T, String> skin) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.group = group;
        this.pool = pool;
        this.skin = skin;
        this.wide = model;
        this.slim = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        list(context, group);
    }

    /** Lists every skin of a group in the loaded resource packs, and shares the list with a local server. */
    private static void list(EntityRendererProvider.Context context, String group) {
        Map<String, List<String>> pools = new HashMap<>();
        String prefix = "textures/entity/" + group;
        for (Identifier id : context.getResourceManager().listResources(prefix, id -> id.getPath().endsWith(".png")).keySet()) {
            if (!id.getNamespace().equals(ProjectRedacted.MOD_ID)) continue;
            String rest = id.getPath().substring(prefix.length() + 1);
            int slash = rest.indexOf('/');
            if (slash < 0) continue;
            pools.computeIfAbsent(rest.substring(0, slash), k -> new ArrayList<>()).add(rest.substring(slash + 1, rest.length() - 4));
        }
        for (List<String> l : pools.values()) Collections.sort(l);
        synchronized (POOLS) {
            POOLS.put(group, pools);
        }
        Skins.clientPool = (g, p) -> {
            synchronized (POOLS) {
                Map<String, List<String>> m = POOLS.get(g);
                return m == null ? null : m.get(p);
            }
        };
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    @Override
    public void extractRenderState(T entity, NpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        String p = pool.apply(entity), s = skin.apply(entity);
        List<String> have;
        synchronized (POOLS) {
            have = POOLS.getOrDefault(group, Map.of()).getOrDefault(p, List.of());
        }
        // A skin this client doesn't have (another player's resource pack) falls back to one it does.
        if (s == null || !have.contains(s)) s = have.isEmpty() ? null : have.get(Math.floorMod(entity.getUUID().hashCode(), have.size()));
        state.texture = s == null ? STEVE : ProjectRedacted.id("textures/entity/" + group + "/" + p + "/" + s + ".png");
        state.slim = Skins.slim(s);
    }

    @Override
    public void submit(NpcRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        model = state.slim ? slim : wide;
        super.submit(state, pose, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(NpcRenderState state) {
        return state.texture;
    }
}
