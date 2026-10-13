package dev.projectredacted.block.deco;

import net.minecraft.world.level.block.IronBarsBlock;

/** Bars, mesh, fencing and window panes; IronBarsBlock's constructor isn't public. */
public class DecoPaneBlock extends IronBarsBlock {
    public DecoPaneBlock(Properties properties) {
        super(properties);
    }
}
