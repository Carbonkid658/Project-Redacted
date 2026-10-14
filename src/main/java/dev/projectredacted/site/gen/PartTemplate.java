package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The blocks of a hand-built part, read from a vanilla structure .nbt file. */
public class PartTemplate {
    public record Entry(BlockPos pos, BlockState state, CompoundTag nbt) {}

    /** The raw file, for the final vanilla placement that restores chests, signs and other block data. */
    public final CompoundTag tag;
    public final List<Entry> blocks = new ArrayList<>();
    public int sx, sy, sz;

    private PartTemplate(CompoundTag tag) {
        this.tag = tag;
    }

    public static PartTemplate parse(CompoundTag tag, HolderGetter<Block> getter) {
        PartTemplate t = new PartTemplate(tag);
        ListTag size = tag.getListOrEmpty("size");
        t.sx = size.getIntOr(0, 0);
        t.sy = size.getIntOr(1, 0);
        t.sz = size.getIntOr(2, 0);
        ListTag palette = tag.getListOrEmpty("palette");
        if (palette.size() == 0) palette = tag.getListOrEmpty("palettes").getListOrEmpty(0);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(getter, palette.getCompoundOrEmpty(i)));
        ListTag blocks = tag.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompoundOrEmpty(i);
            ListTag pos = b.getListOrEmpty("pos");
            int state = b.getIntOr("state", 0);
            if (state < 0 || state >= states.size()) continue;
            t.blocks.add(new Entry(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), states.get(state),
                    b.getCompound("nbt").orElse(null)));
        }
        return t;
    }
}
