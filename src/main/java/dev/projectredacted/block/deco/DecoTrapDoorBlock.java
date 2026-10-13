package dev.projectredacted.block.deco;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Hatches and vent covers. Iron ones open only with redstone. */
public class DecoTrapDoorBlock extends TrapDoorBlock {
    public DecoTrapDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }
}
