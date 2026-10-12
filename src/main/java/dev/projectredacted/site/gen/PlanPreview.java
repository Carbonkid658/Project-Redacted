package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * The ghost outline of a plan (design §6 step 1): the edges of every part box drawn with particles, with the
 * doorways that join something marked green. Shown until the player builds, cancels or it times out.
 */
public final class PlanPreview {
    private static final long TIMEOUT_TICKS = 20 * 60 * 3;

    private record Shown(SitePlan plan, long until) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    public static void show(Player player, SitePlan plan) {
        long now = player.level().getGameTime();
        SHOWN.put(player.getUUID(), new Shown(plan, now + TIMEOUT_TICKS));
    }

    public static void clear(Player player) {
        SHOWN.remove(player.getUUID());
    }

    public static SitePlan shown(Player player) {
        Shown s = SHOWN.get(player.getUUID());
        return s == null ? null : s.plan;
    }

    public static void clearAll() {
        SHOWN.clear();
    }

    /** Runs every 10 ticks. */
    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Shown>> it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Shown> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || player.level().getGameTime() > e.getValue().until) {
                it.remove();
                continue;
            }
            draw((ServerLevel) player.level(), player, e.getValue().plan);
        }
    }

    private static void draw(ServerLevel level, ServerPlayer player, SitePlan plan) {
        int budget = 600;
        BlockPos eye = player.blockPosition();
        List<SitePlan.PlanPart> parts = new ArrayList<>(plan.parts);
        parts.sort(Comparator.comparingDouble(p -> p.box.center().distSqr(eye)));
        for (SitePlan.PlanPart p : parts) {
            Box b = p.box;
            int step = Math.max(1, (b.width() + b.length() + b.height()) / 24);
            for (int x = b.x1(); x <= b.x2(); x += step) {
                budget -= edge(level, x, b.y1(), b.z1(), x, b.y1(), b.z2(), b);
                budget -= edge(level, x, b.y2(), b.z1(), x, b.y2(), b.z2(), b);
            }
            for (int y = b.y1(); y <= b.y2(); y += step) {
                for (int[] c : new int[][]{{b.x1(), b.z1()}, {b.x2(), b.z1()}, {b.x1(), b.z2()}, {b.x2(), b.z2()}}) {
                    level.sendParticles(ParticleTypes.END_ROD, c[0] + 0.5, y + 0.5, c[1] + 0.5, 1, 0, 0, 0, 0);
                    budget--;
                }
            }
            for (int i = 0; i < p.part.connectors.size(); i++) {
                if ((p.used & (1 << i)) == 0) continue;
                BlockPos c = p.connector(i).pos();
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 2, 0.2, 0.3, 0.2, 0);
            }
            if (budget <= 0) return;
        }
    }

    /** Only the two ends of each line across the box, so the outline stays readable. */
    private static int edge(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, Box b) {
        boolean xEdge = x1 == b.x1() || x1 == b.x2();
        if (xEdge) {
            for (int z = z1; z <= z2; z++) level.sendParticles(ParticleTypes.END_ROD, x1 + 0.5, y1 + 0.5, z + 0.5, 1, 0, 0, 0, 0);
            return z2 - z1 + 1;
        }
        level.sendParticles(ParticleTypes.END_ROD, x1 + 0.5, y1 + 0.5, z1 + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, x2 + 0.5, y2 + 0.5, z2 + 0.5, 1, 0, 0, 0, 0);
        return 2;
    }

    private PlanPreview() {}
}
