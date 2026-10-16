package dev.projectredacted.combat;

import dev.projectredacted.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Hitscan shooting for players and NPCs (NPC revamp §4.3). */
public final class Guns {
    /** The gun in a stack, or null. */
    public static GunType of(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun ? gun.type : null;
    }

    /** Fires one shot from {@code shooter}'s eyes along {@code aim}. Returns whether anything living was hit. */
    public static boolean fire(ServerLevel level, LivingEntity shooter, GunType type, Vec3 aim) {
        return fire(level, shooter, type, aim, 1f);
    }

    /** As above, with damage scaled (the Armory's buff to Security, §4.3). */
    public static boolean fire(ServerLevel level, LivingEntity shooter, GunType type, Vec3 aim, float damageScale) {
        Vec3 eye = shooter.getEyePosition();
        boolean hit = false;
        var random = shooter.getRandom();
        for (int i = 0; i < type.pellets; i++) {
            Vec3 dir = aim.normalize().add(random.nextGaussian() * type.spread, random.nextGaussian() * type.spread, random.nextGaussian() * type.spread).normalize();
            Vec3 end = eye.add(dir.scale(type.range));
            HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            AABB sweep = shooter.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
            EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, shooter, eye, end, sweep,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != shooter.getVehicle(), 0.1f);
            Vec3 stop = end;
            if (ehr != null) {
                stop = ehr.getLocation();
                Entity target = ehr.getEntity();
                DamageSource src = shooter instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(shooter);
                // Guns ignore the hurt cooldown between pellets, so a shotgun blast lands every pellet.
                target.setInvulnerableTime(0);
                if (target.hurtServer(level, src, type.damage * damageScale)) hit = true;
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, stop.x, stop.y, stop.z, 2, 0.1, 0.1, 0.1, 0);
            } else if (block.getType() == HitResult.Type.BLOCK) {
                level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 3, 0.05, 0.05, 0.05, 0.01);
            }
            // A faint trail so shots can be seen.
            Vec3 step = stop.subtract(eye);
            int dots = (int) Math.min(12, step.length() / 2);
            for (int d = 1; d <= dots; d++) {
                Vec3 p = eye.add(step.scale(d / (double) (dots + 1)));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        Vec3 muzzle = eye.add(aim.normalize().scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), type.sound(), shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 1.5f,
                0.9f + random.nextFloat() * 0.2f);
        return hit;
    }

    public static void reloadSound(ServerLevel level, LivingEntity who) {
        level.playSound(null, who.getX(), who.getY(), who.getZ(), ModSounds.GUN_RELOAD, SoundSource.NEUTRAL, 0.8f, 1f);
    }

    private Guns() {}
}
