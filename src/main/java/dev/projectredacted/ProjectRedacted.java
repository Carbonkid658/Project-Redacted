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
import dev.projectredacted.site.gen.PartFrames;
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
        ProjectRedactedBlockEntities.register();
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
            PartFrames.load(server);
            runAutotest(server);
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
            PartFrames.unload();
            Gui.clear();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            SiteManager.tick(server);
            Gui.tick(server);
            WanderingContacts.tick(server);
            tickAutotest(server);
        });

        LOG.info("Project [REDACTED] initialized. Site Director, welcome back.");
    }

    private static java.util.ArrayDeque<String> autotest;
    private static int autotestWait;

    /**
     * CI hook: if the server folder has projectredacted-autotest.txt, runs each line as a console command once
     * the server has started ("wait N" pauses N ticks), then stops the server. Used to test site generation.
     */
    private static void runAutotest(net.minecraft.server.MinecraftServer server) {
        java.nio.file.Path file = java.nio.file.Path.of("projectredacted-autotest.txt");
        if (!java.nio.file.Files.exists(file)) return;
        try {
            autotest = new java.util.ArrayDeque<>();
            for (String line : java.nio.file.Files.readAllLines(file)) if (!line.isBlank() && !line.startsWith("#")) autotest.add(line.trim());
        } catch (Exception e) {
            LOG.error("[autotest] failed", e);
            server.halt(false);
        }
    }

    private static void tickAutotest(net.minecraft.server.MinecraftServer server) {
        if (autotest == null) return;
        if (autotestWait > 0) {
            autotestWait--;
            return;
        }
        while (!autotest.isEmpty()) {
            String line = autotest.poll();
            if (line.startsWith("wait ")) {
                autotestWait = Integer.parseInt(line.substring(5).trim());
                LOG.info("[autotest] waiting {} ticks", autotestWait);
                return;
            }
            LOG.info("[autotest] /{}", line);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
            } catch (Exception e) {
                LOG.error("[autotest] {} failed", line, e);
            }
        }
        autotest = null;
        server.halt(false);
    }
}
