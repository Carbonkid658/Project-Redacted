package dev.projectredacted.npc;

import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.util.Time;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Marshall, Carter & Dark contacts roam anywhere; Ways to the Library appear at night. Killing one doesn't stop more coming. */
public final class WanderingContacts {
    private static final Random RANDOM = new Random();
    private static int timer;

    public static void tick(MinecraftServer server) {
        if (++timer % 6000 != 0) return; // every 5 minutes
        ServerLevel level = server.overworld();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            if (RANDOM.nextDouble() < 0.10 && noneNearby(level, player, TraderKind.MCD_CONTACT)) {
                spawnNear(level, player, TraderKind.MCD_CONTACT, 12000);
            }
            if (Time.night(level) && RANDOM.nextDouble() < 0.05 && noneNearby(level, player, TraderKind.LIBRARY_ENTRANCE)) {
                spawnNear(level, player, TraderKind.LIBRARY_ENTRANCE, 6000);
            }
        }
    }

    private static boolean noneNearby(ServerLevel level, ServerPlayer player, TraderKind kind) {
        return level.getEntitiesOfClass(TraderEntity.class, player.getBoundingBox().inflate(96), t -> t.kind() == kind).isEmpty();
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player, TraderKind kind, int lifetime) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        int dist = 20 + RANDOM.nextInt(12);
        int x = (int) (player.getX() + Math.cos(angle) * dist), z = (int) (player.getZ() + Math.sin(angle) * dist);
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!level.isLoaded(pos)) return;
        TraderEntity e = ModEntities.TRADER.create(level, EntitySpawnReason.EVENT);
        if (e == null) return;
        e.setKind(kind);
        e.leaveAt = level.getGameTime() + lifetime;
        e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360, 0);
        level.addFreshEntity(e);
    }

    private WanderingContacts() {}
}
