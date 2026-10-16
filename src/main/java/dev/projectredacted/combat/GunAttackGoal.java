package dev.projectredacted.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * NPCs with guns (NPC revamp §4.3): keep their distance and shoot when they can see the target, reloading on a
 * timer instead of using ammo items. When the target gets very close this goal steps aside, so the mob's melee
 * goal (lower priority) takes over.
 */
public class GunAttackGoal extends Goal {
    /** Closer than this, it's melee. */
    public static final double MELEE = 3;
    private static final double NEAR = 6, FAR = 14;

    private final PathfinderMob mob;
    private final BooleanSupplier allowed;
    private LivingEntity target;
    private int cooldown, reload, inMag = -1, seen, strafe;

    public GunAttackGoal(PathfinderMob mob, BooleanSupplier allowed) {
        this.mob = mob;
        this.allowed = allowed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private GunType gun() {
        return Guns.of(mob.getMainHandItem());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || gun() == null || !allowed.getAsBoolean()) return false;
        return mob.distanceToSqr(t) > MELEE * MELEE;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        target = mob.getTarget();
        seen = 0;
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        target = mob.getTarget();
        GunType gun = gun();
        if (target == null || gun == null || !(mob.level() instanceof ServerLevel level)) return;
        if (inMag < 0) inMag = gun.magazine;
        mob.getLookControl().setLookAt(target, 30f, 30f);
        double dist = Math.sqrt(mob.distanceToSqr(target));
        boolean sees = mob.getSensing().hasLineOfSight(target);
        seen = sees ? Math.min(seen + 1, 40) : 0;
        double far = Math.min(FAR, gun.range * 0.7);

        // Keep the distance: close in when far or out of sight, back off when too near, else hold and sidestep a little.
        if (!sees || dist > far) {
            mob.getNavigation().moveTo(target, 1.1);
        } else if (dist < NEAR) {
            Vec3 away = DefaultRandomPos.getPosAway(mob, 8, 4, target.position());
            if (away != null && mob.getNavigation().isDone()) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
        } else {
            mob.getNavigation().stop();
            if (--strafe <= 0) {
                strafe = 30 + mob.getRandom().nextInt(40);
                Vec3 side = target.position().subtract(mob.position()).normalize().cross(new Vec3(0, 1, 0)).scale(mob.getRandom().nextBoolean() ? 2 : -2);
                Vec3 p = mob.position().add(side);
                mob.getNavigation().moveTo(p.x, p.y, p.z, 0.8);
            }
        }

        if (reload > 0) {
            if (--reload == 0) inMag = gun.magazine;
            return;
        }
        if (--cooldown > 0 || seen < 5 || dist > gun.range) return;
        // Aim at the chest, with some wobble that grows with range.
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(mob.getEyePosition());
        double wobble = 0.02 + dist * 0.003;
        aim = aim.normalize().add(mob.getRandom().nextGaussian() * wobble, mob.getRandom().nextGaussian() * wobble, mob.getRandom().nextGaussian() * wobble);
        Guns.fire(level, mob, gun, aim, damageScale());
        cooldown = gun.cooldown + mob.getRandom().nextInt(6);
        if (--inMag <= 0) {
            reload = gun.reload;
            Guns.reloadSound(level, mob);
        }
    }

    /** Strength (the Armory buff, Sharpshooter, veteran levels) adds a quarter of the damage per level. */
    private float damageScale() {
        MobEffectInstance s = mob.getEffect(MobEffects.STRENGTH);
        return s == null ? 1f : 1f + 0.25f * (s.getAmplifier() + 1);
    }
}
