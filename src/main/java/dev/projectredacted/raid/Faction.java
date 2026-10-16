package dev.projectredacted.raid;

import dev.projectredacted.combat.GunType;

/** Groups that raid sites. MTF fight for the Foundation. Raiding factions attack the player and each other. */
public enum Faction {
    GOC("Global Occult Coalition", "goc", true, GunType.RIFLE),
    SERPENTS_HAND("Serpent's Hand", "serpents_hand", true, GunType.PISTOL),
    CHAOS_INSURGENCY("Chaos Insurgency", "chaos_insurgency", true, GunType.SHOTGUN),
    MTF("Mobile Task Force", "mtf", false, GunType.PISTOL, GunType.RIFLE);

    public final String displayName;
    public final String skin;
    public final boolean raider;
    /** The guns this faction carries (NPC revamp §4.3); each member gets one of them. */
    public final GunType[] guns;

    Faction(String displayName, String skin, boolean raider, GunType... guns) {
        this.displayName = displayName;
        this.skin = skin;
        this.raider = raider;
        this.guns = guns;
    }
}
