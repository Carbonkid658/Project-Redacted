package dev.projectredacted.client;

import dev.projectredacted.ProjectRedacted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CI hook: if the client run folder has projectredacted-clienttest.txt, opens Create World from the title screen,
 * creates the world, waits for it to load and quits. A watchdog dumps every thread if a step hangs.
 */
public final class ClientTest {
    private static int step;
    private static int ticks;
    private static volatile long lastProgress = System.currentTimeMillis();
    private static volatile String stage = "starting";

    public static void init() {
        if (!Files.exists(Path.of("projectredacted-clienttest.txt"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ClientTest::tick);
        Thread watchdog = new Thread(ClientTest::watch, "projectredacted-clienttest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void progress(String s) {
        stage = s;
        lastProgress = System.currentTimeMillis();
        ProjectRedacted.LOG.info("[clienttest] {}", s);
    }

    private static void tick(Minecraft mc) {
        ticks++;
        Screen screen = currentScreen(mc);
        String name = screen == null ? "none" : screen.getClass().getName();
        if (ticks % 100 == 0) ProjectRedacted.LOG.info("[clienttest] tick {} stage '{}' screen {}", ticks, stage, name);
        try {
            switch (step) {
                case 0 -> {
                    if (name.endsWith("OnboardingScreen")) mc.gui.setScreen(new TitleScreen());
                    else if (screen instanceof TitleScreen) {
                        progress("title screen; opening Create World");
                        step = 1;
                        Method best = null;
                        for (Method m : Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen").getDeclaredMethods()) {
                            if (m.getName().equals("openFresh") && (best == null || m.getParameterCount() < best.getParameterCount())) best = m;
                        }
                        for (Method m : new Method[]{best}) {
                            m.setAccessible(true);
                            Object[] args = new Object[m.getParameterCount()];
                            Class<?>[] types = m.getParameterTypes();
                            for (int i = 0; i < args.length; i++) {
                                if (types[i] == Minecraft.class) args[i] = mc;
                                else if (types[i] == Screen.class) args[i] = screen;
                                else if (types[i] == Runnable.class) args[i] = (Runnable) () -> mc.gui.setScreen(new TitleScreen());
                            }
                            ProjectRedacted.LOG.info("[clienttest] calling {}", m);
                            m.invoke(null, args);
                            break;
                        }
                    }
                }
                case 1 -> {
                    if (name.endsWith("CreateWorldScreen")) {
                        progress("Create World screen is open; creating the world");
                        step = 2;
                        for (Method m : screen.getClass().getDeclaredMethods()) {
                            if (m.getName().equals("onCreate") && m.getParameterCount() == 0) {
                                m.setAccessible(true);
                                m.invoke(screen);
                            }
                        }
                    }
                }
                case 2 -> {
                    if (mc.level != null && mc.player != null) {
                        progress("world loaded with the player in it");
                        step = 3;
                        ticks = 0;
                    }
                }
                case 3 -> {
                    if (ticks > 200) {
                        progress("done, quitting");
                        step = 4;
                        mc.stop();
                    }
                }
                default -> {}
            }
        } catch (Throwable t) {
            ProjectRedacted.LOG.error("[clienttest] step {} failed", step, t);
            step = 4;
            mc.stop();
        }
    }

    /** The open screen; looked up by reflection since its accessor moved in 26.x. */
    private static Screen currentScreen(Minecraft mc) {
        for (Object holder : new Object[]{mc.gui, mc}) {
            for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (!Screen.class.isAssignableFrom(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(holder);
                        if (v instanceof Screen sc) return sc;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static void watch() {
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
            if (step >= 4) return;
            if (System.currentTimeMillis() - lastProgress < 150_000) continue;
            StringBuilder sb = new StringBuilder("[clienttest] HUNG at stage '" + stage + "'; thread dump:\n");
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                sb.append("[clienttest] thread ").append(e.getKey().getName()).append(" ").append(e.getKey().getState()).append('\n');
                for (StackTraceElement el : e.getValue()) sb.append("[clienttest]     at ").append(el).append('\n');
            }
            ProjectRedacted.LOG.error(sb.toString());
            Runtime.getRuntime().halt(3);
        }
    }

    private ClientTest() {}
}
