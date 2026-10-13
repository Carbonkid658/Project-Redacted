package dev.projectredacted.client;

import dev.projectredacted.client.renderers.CassieRenderer;
import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.registry.ProjectRedactedBlockEntities;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.client.screen.ClientGui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class ProjectRedactedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.STAFF, ctx -> new NpcRenderer<StaffEntity>(ctx, "staff", StaffEntity::skinId));
        EntityRendererRegistry.register(ModEntities.ARMED_NPC, ctx -> new NpcRenderer<ArmedNpcEntity>(ctx, "faction", ArmedNpcEntity::skinId));
        EntityRendererRegistry.register(ModEntities.TRADER, ctx -> new NpcRenderer<TraderEntity>(ctx, "trader", TraderEntity::skinId));
        ClientGui.init();

            BlockEntityRenderers.register(
                    ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY,
                    context -> new CassieRenderer<>(context, ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY)
            );
    }

}
