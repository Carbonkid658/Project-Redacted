package dev.projectredacted.util;

import net.minecraft.world.level.Level;

/** Day/night helpers kept in one place, since the time API is one of the things that moves between versions. */
public final class Time {
    public static long dayTime(Level level) {
        return level.getOverworldClockTime() % 24000L;
    }

    public static long day(Level level) {
        return level.getOverworldClockTime() / 24000L;
    }

    public static boolean night(Level level) {
        long t = dayTime(level);
        return t >= 13000 && t < 23000;
    }

    private Time() {}
}
