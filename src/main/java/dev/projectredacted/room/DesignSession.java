package dev.projectredacted.room;

import dev.projectredacted.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * A player's in-progress room design with the Room Configuration Device: two corners, a room type, a rotation
 * and a seed. While previewing, the planned blocks show as ghost particles that only follow the plan, so the
 * player can rotate, reroll or adjust the corners before building.
 */
public class DesignSession {
    private static final Map<UUID, DesignSession> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();

    public BlockPos cornerA, cornerB;
    public String typeId;
    public int rotation;
    public long seed = RANDOM.nextLong();
    public boolean previewing;

    private transient RoomBuilder.Plan cachedPlan;
    private transient String cachedKey;

    public static DesignSession get(Player player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), k -> new DesignSession());
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public void reset() {
        cornerA = cornerB = null;
        typeId = null;
        rotation = 0;
        seed = RANDOM.nextLong();
        previewing = false;
        cachedPlan = null;
    }

    public void reroll() {
        seed = RANDOM.nextLong();
    }

    public String describeSize() {
        if (cornerA == null || cornerB == null) return "?";
        return (Math.abs(cornerA.getX() - cornerB.getX()) + 1) + " x " + (Math.abs(cornerA.getZ() - cornerB.getZ()) + 1)
                + " x " + (Math.abs(cornerA.getY() - cornerB.getY()) + 1);
    }

    public Room draftRoom(int number) {
        return new Room(number, typeId, cornerA, cornerB, rotation, seed);
    }

    public RoomBuilder.Plan plan() {
        RoomType type = RoomTypes.get(typeId);
        if (type == null || cornerA == null || cornerB == null) return null;
        String key = typeId + cornerA + cornerB + rotation + seed;
        if (!key.equals(cachedKey)) {
            cachedKey = key;
            cachedPlan = RoomBuilder.plan(draftRoom(0), type);
        }
        return cachedPlan;
    }

    public static void tickPreviews(MinecraftServer server) {
        for (Map.Entry<UUID, DesignSession> e : SESSIONS.entrySet()) {
            DesignSession s = e.getValue();
            if (!s.previewing) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.getMainHandItem().is(ModItems.ROOM_DESIGNATOR)) continue;
            RoomBuilder.Plan plan = s.plan();
            if (plan == null) continue;
            ServerLevel level = (ServerLevel) player.level();
            List<RoomBuilder.Placement> shown = new ArrayList<>();
            for (RoomBuilder.Placement p : plan.placements) {
                if (!p.structural() || p.state().isAir()) continue;
                if (level.getBlockState(p.pos()).equals(p.state())) continue;
                shown.add(p);
            }
            Collections.shuffle(shown, RANDOM);
            int limit = Math.min(250, shown.size());
            for (int i = 0; i < limit; i++) {
                RoomBuilder.Placement p = shown.get(i);
                boolean furniture = p.pos().getY() > Math.min(s.cornerA.getY(), s.cornerB.getY())
                        && p.pos().getY() < Math.max(s.cornerA.getY(), s.cornerB.getY()) && !isWall(s, p.pos());
                level.sendParticles(furniture ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD,
                        p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    private static boolean isWall(DesignSession s, BlockPos p) {
        int x1 = Math.min(s.cornerA.getX(), s.cornerB.getX()), x2 = Math.max(s.cornerA.getX(), s.cornerB.getX());
        int z1 = Math.min(s.cornerA.getZ(), s.cornerB.getZ()), z2 = Math.max(s.cornerA.getZ(), s.cornerB.getZ());
        return p.getX() == x1 || p.getX() == x2 || p.getZ() == z1 || p.getZ() == z2;
    }
}
