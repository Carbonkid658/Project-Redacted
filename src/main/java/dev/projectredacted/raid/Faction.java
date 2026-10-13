package dev.projectredacted.raid;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Groups that raid sites. MTF fight for the Foundation. Raiding factions attack the player and each other. */
public enum Faction {
    GOC("Global Occult Coalition", "goc", Items.IRON_SWORD, true),
    SERPENTS_HAND("Serpent's Hand", "serpents_hand", Items.IRON_AXE, true),
    CHAOS_INSURGENCY("Chaos Insurgency", "chaos_insurgency", Items.IRON_AXE, true),
    MTF("Mobile Task Force", "mtf", Items.DIAMOND_SWORD, false);

    public final String displayName;
    public final String skin;
    public final Item weapon;
    public final boolean raider;

    Faction(String displayName, String skin, Item weapon, boolean raider) {
        this.displayName = displayName;
        this.skin = skin;
        this.weapon = weapon;
        this.raider = raider;
    }
}
