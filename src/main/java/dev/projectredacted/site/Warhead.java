package dev.projectredacted.site;

import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The Alpha Warhead: a 90 second countdown, then the site goes up. The player can always rebuild. */
public final class Warhead {
    private static final int COUNTDOWN = 90 * 20;
    private static final Map<String, long[]> ARMED = new HashMap<>(); // site id -> {detonate tick, x, y, z}

    public static void arm(Site site, BlockPos pos) {
        ServerLevel level = SiteManager.overworld();
        if (level == null || ARMED.containsKey(site.id)) return;
        ARMED.put(site.id, new long[]{level.getGameTime() + COUNTDOWN, pos.getX(), pos.getY(), pos.getZ()});
        String msg = "Alpha Warhead detonation sequence engaged. The underground section of the facility will be detonated in T-minus 90 seconds.";
        if (!Cassie.announce(site, msg)) Cassie.alarm(site, msg);
    }

    /** Ticks until detonation, or -1 when the warhead isn't armed. */
    public static long ticksLeft(Site site) {
        long[] armed = ARMED.get(site.id);
        ServerLevel level = SiteManager.overworld();
        return armed == null || level == null ? -1 : Math.max(0, armed[0] - level.getGameTime());
    }

    public static void abort(Site site) {
        if (ARMED.remove(site.id) != null) {
            if (!Cassie.announce(site, "Detonation cancelled. Restarting all systems.")) Cassie.alarm(site, "Detonation cancelled.");
        }
    }

    static void tick(ServerLevel level, Site site) {
        long[] armed = ARMED.get(site.id);
        if (armed == null) return;
        long left = armed[0] - level.getGameTime();
        BlockPos pos = new BlockPos((int) armed[1], (int) armed[2], (int) armed[3]);
        if (level.isLoaded(pos) && !level.getBlockState(pos).is(ModBlocks.ALPHA_WARHEAD)) {
            ARMED.remove(site.id);
            Cassie.alarm(site, "Warhead destroyed. Detonation aborted.");
            return;
        }
        if (left > 0 && left % 200 == 0) {
            Cassie.alarm(site, "T-minus " + (left / 20) + " seconds.");
            for (ServerPlayer p : Cassie.playersInSite(site)) {
                level.playSound(null, p.blockPosition(), ModSounds.WARHEAD_ALARM, SoundSource.BLOCKS, 1f, 1f);
            }
        }
        if (left > 0) return;
        ARMED.remove(site.id);
        Random random = new Random();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20f, Level.ExplosionInteraction.TNT);
        for (int i = 0; i < 10; i++) {
            BlockPos p = pos.offset(random.nextInt(48) - 24, random.nextInt(12) - 6, random.nextInt(48) - 24);
            level.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12f, Level.ExplosionInteraction.TNT);
        }
        site.log("The Alpha Warhead was detonated.");
    }

    private Warhead() {}
}
