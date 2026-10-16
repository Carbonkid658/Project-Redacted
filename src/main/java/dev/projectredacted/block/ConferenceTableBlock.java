package dev.projectredacted.block;

import dev.projectredacted.blockentities.ConferenceTableBlockEntity;
import dev.projectredacted.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Conference Room's key block: a 4 by 2 table (geckolib/models/conference_table.geo.json). It runs from
 * where you place it to the right for 4 blocks and away from you for 2.
 */
public class ConferenceTableBlock extends MultiBlockKey {
    private static final int[][] OFFSETS = {{1, 0, 0}, {2, 0, 0}, {3, 0, 0}, {0, 0, 1}, {1, 0, 1}, {2, 0, 1}, {3, 0, 1}};

    public ConferenceTableBlock(Properties properties) {
        super(properties, () -> ModBlocks.CONFERENCE_TABLE_PART);
    }

    @Override
    protected int[][] offsets() {
        return OFFSETS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConferenceTableBlockEntity(pos, state);
    }
}
