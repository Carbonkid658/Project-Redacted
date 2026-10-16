package dev.projectredacted.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/** Pushes a raider toward its faction's current objective, breaking through walls and doors when the way is blocked. */
public class RaidObjectiveGoal extends Goal {
    private final ArmedNpcEntity mob;
    private BlockPos objective;
    private BlockPos breaking;
    private int breakProgress, breakNeeded;
    private int repath;
    private int stuckTicks;
    private double lastDist = Double.MAX_VALUE;

    public RaidObjectiveGoal(ArmedNpcEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel) || mob.getTarget() != null) return false;
        objective = RaidManager.objective(mob);
        return objective != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && objective != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        resetBreaking();
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) mob.level();
        if (--repath <= 0) {
            repath = 20;
            objective = RaidManager.objective(mob);
            if (objective == null) return;
            double dist = mob.blockPosition().distSqr(objective);
            if (dist <= 9) {
                RaidManager.arrive(level, mob, objective);
                return;
            }
            // Into a room through its Room Entrance Marker (NPC revamp §4.2), then on to the objective.
            BlockPos way = viaDoor(objective);
            mob.getNavigation().moveTo(way.getX() + 0.5, way.getY(), way.getZ() + 0.5, 1.0);
            if (dist >= lastDist - 1) stuckTicks += 20;
            else stuckTicks = 0;
            lastDist = dist;
        }
        if (objective == null) return;
        if (stuckTicks >= 60 || mob.getNavigation().isDone()) breakThrough(level);
    }

    /** The room's doorway while outside a room the objective is in, else the objective itself. */
    private BlockPos viaDoor(BlockPos target) {
        var site = mob.site();
        var room = site == null ? null : site.roomAt(target);
        if (room == null || room.entrance == null || room.contains(mob.blockPosition())) return target;
        BlockPos door = room.outsideDoor();
        return mob.blockPosition().distSqr(door) > 4 ? door : target;
    }

    private void breakThrough(ServerLevel level) {
        if (breaking == null) {
            breaking = findObstacle(level);
            if (breaking == null) return;
            float hardness = level.getBlockState(breaking).getDestroySpeed(level, breaking);
            breakNeeded = (int) (20 + hardness * 8);
            breakProgress = 0;
        }
        BlockState state = level.getBlockState(breaking);
        if (state.isAir()) {
            resetBreaking();
            return;
        }
        breakProgress++;
        level.destroyBlockProgress(mob.getId(), breaking, Math.min(9, breakProgress * 10 / breakNeeded));
        if (breakProgress >= breakNeeded) {
            level.destroyBlock(breaking, false, mob);
            resetBreaking();
            stuckTicks = 0;
            lastDist = Double.MAX_VALUE;
        }
    }

    private void resetBreaking() {
        if (breaking != null) mob.level().destroyBlockProgress(mob.getId(), breaking, -1);
        breaking = null;
        breakProgress = 0;
    }

    private BlockPos findObstacle(ServerLevel level) {
        BlockPos feet = mob.blockPosition();
        int dx = objective.getX() - feet.getX(), dz = objective.getZ() - feet.getZ();
        Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos[] candidates;
        if (objective.getY() < feet.getY() - 1 && Math.abs(dx) + Math.abs(dz) < 3) candidates = new BlockPos[]{feet.below()};
        else if (objective.getY() > feet.getY() + 1 && Math.abs(dx) + Math.abs(dz) < 3) candidates = new BlockPos[]{feet.above(2), feet.above()};
        else candidates = new BlockPos[]{feet.relative(dir), feet.relative(dir).above()};
        for (BlockPos p : candidates) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 30) continue;
            return p;
        }
        return null;
    }
}
