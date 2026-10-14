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
        // Room Entrance Markers show while a creative player holds one (or a barrier or light block), like barriers;
        // Area Hall Markers (and the entrance markers near them) while anyone holds an Area Hall Marker.
        dev.projectredacted.block.RoomEntranceMarkerBlock.holding = () -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return false;
            for (var stack : new net.minecraft.world.item.ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.is(ModBlocks.AREA_HALL_MARKER.asItem())) return true;
                if (!player.isCreative()) continue;
                if (stack.is(ModBlocks.ROOM_ENTRANCE_MARKER.asItem()) || stack.is(net.minecraft.world.item.Items.BARRIER) || stack.is(net.minecraft.world.item.Items.LIGHT)) return true;
            }
            return false;
        };

            BlockEntityRenderers.register(
                    ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY,
                    context -> new CassieRenderer<>(context, ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY)
            );
    }

}
