package dev.projectredacted.world;

import dev.projectredacted.npc.TraderEntity;
import dev.projectredacted.npc.TraderKind;
import dev.projectredacted.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A GOC outpost or a Dr. Wondertainment stall: a small fixed spot with a trader who stays there. */
public class ContactPostFeature extends Feature<NoneFeatureConfiguration> {
    private final TraderKind kind;

    public ContactPostFeature(TraderKind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos o = ctx.origin();
        int ox = o.getX(), oz = o.getZ(), y = o.getY();
        boolean goc = kind == TraderKind.GOC_QUARTERMASTER;
        BlockState floor = goc ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.PINK_CONCRETE.defaultBlockState();
        BlockState post = goc ? Blocks.IRON_BARS.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
        BlockState roof = goc ? Blocks.GREEN_WOOL.defaultBlockState() : Blocks.YELLOW_WOOL.defaultBlockState();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                GenUtil.set(level, ox + x, y - 1, oz + z, floor);
                for (int h = 0; h < 3; h++) GenUtil.set(level, ox + x, y + h, oz + z, Blocks.AIR.defaultBlockState());
                boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                if (corner) for (int h = 0; h < 3; h++) GenUtil.set(level, ox + x, y + h, oz + z, post);
                BlockState top = goc ? roof : ((x + z) % 2 == 0 ? Blocks.RED_WOOL.defaultBlockState() : roof);
                GenUtil.set(level, ox + x, y + 3, oz + z, top);
            }
        }
        for (int x = -2; x <= 2; x++) GenUtil.set(level, ox + x, y, oz - 2, goc ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : Blocks.BIRCH_SLAB.defaultBlockState());
        GenUtil.set(level, ox + 2, y, oz + 2, goc ? Blocks.SMITHING_TABLE.defaultBlockState() : Blocks.CAKE.defaultBlockState());
        GenUtil.set(level, ox, y + 2, oz, Blocks.LANTERN.defaultBlockState());
        if (goc) GenUtil.lootChest(level, new BlockPos(ox - 2, y, oz + 2), random, false);

        TraderEntity trader = ModEntities.TRADER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (trader != null) {
            trader.setKind(kind);
            trader.setPersistenceRequired();
            trader.snapTo(ox + 0.5, y, oz - 0.5, 180, 0);
            level.addFreshEntity(trader);
        }
        return true;
    }
}
