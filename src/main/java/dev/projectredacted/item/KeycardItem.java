package dev.projectredacted.item;

import net.minecraft.world.item.Item;

/** Foundation keycard. Level 4 opens Foundation Bunkers; founding a site grants one. */
public class KeycardItem extends Item {
    private final int level;

    public KeycardItem(int level, Properties properties) {
        super(properties);
        this.level = level;
    }

    public int level() {
        return level;
    }
}
