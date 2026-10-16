package dev.projectredacted.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.Predicate;

/** Looks around every half second and targets the nearest visible entity that matches. */
public class ScanTargetGoal extends Goal {
    private final Mob mob;
    private final Predicate<LivingEntity> predicate;
    private final double range;
    private int cooldown;

    public ScanTargetGoal(Mob mob, double range, Predicate<LivingEntity> predicate) {
        this.mob = mob;
        this.range = range;
        this.predicate = predicate;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) return false;
        cooldown = 10;
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive()) return false;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
                e -> e != mob && e.isAlive() && predicate.test(e))) {
            double d = mob.distanceToSqr(e);
            if (d < bestDist && mob.hasLineOfSight(e)) {
                best = e;
                bestDist = d;
            }
        }
        if (best != null) mob.setTarget(best);
        return false;
    }
}
