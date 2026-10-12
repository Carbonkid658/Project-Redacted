package dev.projectredacted.client;

import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.staff.StaffEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public class ProjectRedactedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.STAFF, ctx -> new NpcRenderer<StaffEntity>(ctx, "staff", StaffEntity::skinId));
        EntityRendererRegistry.register(ModEntities.ARMED_NPC, ctx -> new NpcRenderer<ArmedNpcEntity>(ctx, "faction", ArmedNpcEntity::skinId));
        EntityRendererRegistry.register(ModEntities.TRADER, ctx -> new NpcRenderer<TraderEntity>(ctx, "trader", TraderEntity::skinId));

        BlockRenderLayerMap.putBlock(ModBlocks.REINFORCED_GLASS, ChunkSectionLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.REINFORCED_DOOR, ChunkSectionLayer.CUTOUT);
    }
}
