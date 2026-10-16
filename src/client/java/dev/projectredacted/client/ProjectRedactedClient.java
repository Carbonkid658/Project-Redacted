package dev.projectredacted.client;

import dev.projectredacted.client.renderers.CassieRenderer;
import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.registry.ProjectRedactedBlockEntities;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.client.screen.ClientGui;
import dev.projectredacted.blockentities.DyedBlockEntity;
import dev.projectredacted.client.renderers.ComputerRenderer;
import dev.projectredacted.client.renderers.ConferenceTableRenderer;
import dev.projectredacted.client.renderers.MainframeRenderer;
import dev.projectredacted.client.renderers.VentilationFanRenderer;
import dev.projectredacted.client.renderers.SiteDirectorComputerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import java.util.List;

public class ProjectRedactedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.STAFF, ctx -> new NpcRenderer<StaffEntity>(ctx, "staff", StaffEntity::skinPool, StaffEntity::skin));
        EntityRendererRegistry.register(ModEntities.ARMED_NPC, ctx -> new NpcRenderer<ArmedNpcEntity>(ctx, "faction", ArmedNpcEntity::skinPool, ArmedNpcEntity::skin));
        EntityRendererRegistry.register(ModEntities.TRADER, ctx -> new NpcRenderer<TraderEntity>(ctx, "trader", TraderEntity::skinPool, TraderEntity::skin));
        ClientGui.init();
        // Room Entrance Markers show while a creative player holds one (or a barrier or light block), like barriers.
        dev.projectredacted.block.RoomEntranceMarkerBlock.holding = () -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return false;
            for (var stack : new net.minecraft.world.item.ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                if (!player.isCreative()) continue;
                if (stack.is(ModBlocks.ROOM_ENTRANCE_MARKER.asItem()) || stack.is(net.minecraft.world.item.Items.BARRIER) || stack.is(net.minecraft.world.item.Items.LIGHT)) return true;
            }
            return false;
        };

        // Dyeable blocks take their color from their block entity. Registered once the game has its BlockColors.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.getBlockColors().register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return 0xFFFFFFFF;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof DyedBlockEntity dyed ? 0xFF000000 | dyed.color() : 0xFFFFFFFF;
            }
        }), ModBlocks.DYEABLE_BRICKS, ModBlocks.DYEABLE_FLOOR, ModBlocks.DYEABLE_TILE));

            BlockEntityRenderers.register(
                    ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY,
                    context -> new CassieRenderer<>(context, ProjectRedactedBlockEntities.CASSIE_BLOCK_ENTITY)
            );
            BlockEntityRenderers.register(ProjectRedactedBlockEntities.MAINFRAME_BLOCK_ENTITY, context -> new MainframeRenderer<>(context));
            BlockEntityRenderers.register(ProjectRedactedBlockEntities.SITE_DIRECTOR_COMPUTER_BLOCK_ENTITY, context -> new SiteDirectorComputerRenderer<>(context));
            BlockEntityRenderers.register(ProjectRedactedBlockEntities.COMPUTER_BLOCK_ENTITY, context -> new ComputerRenderer<>(context));
            BlockEntityRenderers.register(ProjectRedactedBlockEntities.CONFERENCE_TABLE_BLOCK_ENTITY, context -> new ConferenceTableRenderer<>(context));
            BlockEntityRenderers.register(ProjectRedactedBlockEntities.VENTILATION_FAN_BLOCK_ENTITY, context -> new VentilationFanRenderer<>(context));
    }

}
