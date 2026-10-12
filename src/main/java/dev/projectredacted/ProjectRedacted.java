package dev.projectredacted;

import dev.projectredacted.command.SiteCommand;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.RoomPreviewScreen;
import dev.projectredacted.net.ModNetwork;
import dev.projectredacted.npc.WanderingContacts;
import dev.projectredacted.registry.*;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.ClaimProtection;
import dev.projectredacted.site.SiteEvents;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.PartKit;
import dev.projectredacted.site.gen.SiteTypeRules;
import dev.projectredacted.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Project [REDACTED]: found and run an SCP Foundation site after the end of the world.
 *
 * The individual SCPs are intentionally not part of this mod yet. Everything that touches
 * anomalies goes through {@link dev.projectredacted.api.ScpApi} so SCPs can be added later.
 */
public class ProjectRedacted implements ModInitializer {
    public static final String MOD_ID = "projectredacted";
    public static final Logger LOG = LoggerFactory.getLogger("Project [REDACTED]");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
        ModTabs.init();
        ModWorldgen.init();
        ModNetwork.init();
        RoomPreviewScreen.registerKeys();

        ClaimProtection.init();
        SiteEvents.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SiteCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RoomTypes.reload(server.getResourceManager());
            SiteTypeRules.reload(server.getResourceManager());
            PartKit.reload(server.getResourceManager());
            SiteManager.load(server);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RoomTypes.reload(server.getResourceManager());
                SiteTypeRules.reload(server.getResourceManager());
                PartKit.reload(server.getResourceManager());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SiteManager.save());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SiteManager.unload();
            Gui.clear();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            SiteManager.tick(server);
            Gui.tick(server);
            WanderingContacts.tick(server);
        });

        LOG.info("Project [REDACTED] initialized. Site Director, welcome back.");
    }
}
