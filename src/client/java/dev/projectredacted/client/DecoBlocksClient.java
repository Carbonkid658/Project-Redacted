package dev.projectredacted.client;

import dev.projectredacted.registry.DecoBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

/** See-through deco blocks need the cutout or translucent layer, or their transparent pixels render black. */
final class DecoBlocksClient {
    static void init() {
        for (Block block : DecoBlocks.CUTOUT) BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT);
        for (Block block : DecoBlocks.TRANSLUCENT) BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.TRANSLUCENT);
    }

    private DecoBlocksClient() {}
}
