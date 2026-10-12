package dev.projectredacted.util;

import java.util.ArrayList;
import java.util.List;

/** Runs work on the next server tick, e.g. opening a new menu from inside a click handler. */
public final class Scheduler {
    private static final List<Runnable> NEXT = new ArrayList<>();

    public static synchronized void next(Runnable task) {
        NEXT.add(task);
    }

    public static void tick() {
        List<Runnable> tasks;
        synchronized (Scheduler.class) {
            if (NEXT.isEmpty()) return;
            tasks = new ArrayList<>(NEXT);
            NEXT.clear();
        }
        tasks.forEach(Runnable::run);
    }

    private Scheduler() {}
}
