package dev.projectredacted.combat;

import dev.projectredacted.registry.ModItems;
import dev.projectredacted.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * The gun set (NPC revamp §4.3). Players use up one ammo item per shot; NPCs have a magazine and reload on a
 * timer instead. Numbers are a first pass, to be balanced.
 */
public enum GunType {
    PISTOL("Pistol", 5f, 1, 0.015, 32, 8, 12, 30),
    RIFLE("Rifle", 8f, 1, 0.006, 48, 12, 20, 50),
    SHOTGUN("Shotgun", 3f, 6, 0.11, 16, 24, 6, 60);

    public final String displayName;
    /** Damage per pellet, pellets per shot, and how far shots wander (radians, roughly). */
    public final float damage;
    public final int pellets;
    public final double spread;
    public final int range;
    /** Ticks between shots. */
    public final int cooldown;
    /** NPC magazine size and reload time (ticks). */
    public final int magazine, reload;

    GunType(String displayName, float damage, int pellets, double spread, int range, int cooldown, int magazine, int reload) {
        this.displayName = displayName;
        this.damage = damage;
        this.pellets = pellets;
        this.spread = spread;
        this.range = range;
        this.cooldown = cooldown;
        this.magazine = magazine;
        this.reload = reload;
    }

    public Item ammo() {
        return switch (this) {
            case PISTOL -> ModItems.PISTOL_AMMO;
            case RIFLE -> ModItems.RIFLE_AMMO;
            case SHOTGUN -> ModItems.SHOTGUN_SHELLS;
        };
    }

    public Item item() {
        return switch (this) {
            case PISTOL -> ModItems.PISTOL;
            case RIFLE -> ModItems.RIFLE;
            case SHOTGUN -> ModItems.SHOTGUN;
        };
    }

    public SoundEvent sound() {
        return switch (this) {
            case PISTOL -> ModSounds.GUN_PISTOL;
            case RIFLE -> ModSounds.GUN_RIFLE;
            case SHOTGUN -> ModSounds.GUN_SHOTGUN;
        };
    }
}
