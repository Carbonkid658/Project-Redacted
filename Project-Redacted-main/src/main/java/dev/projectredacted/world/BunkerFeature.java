package dev.projectredacted.world;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.staff.StaffSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Random;

/**
 * A Foundation Bunker: a buried shelter behind a Level 4 keycard door, with 1 to 4 survivors who can be
 * recruited. Bunkers spawn randomly and are relatively safe.
 */
public class BunkerFeature implements Feature {
    public static final MapCodec<BunkerFeature> CODEC = MapCodec.unit(BunkerFeature::new);

    @Override
    public MapCodec<BunkerFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos o) {
        int ox = o.getX(), oz = o.getZ(), sy = o.getY();
        if (sy <= level.getMinY() + 12) return false;
        BlockState concrete = Blocks.CONCRETE.gray().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int floor = sy - 7;

        // The shelter itself.
        GenUtil.box(level, ox - 4, floor, oz - 4, ox + 4, sy - 1, oz + 4, concrete, air);
        for (int x = ox - 3; x <= ox + 3; x++) for (int z = oz - 3; z <= oz + 3; z++) GenUtil.set(level, x, floor, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        GenUtil.set(level, ox, sy - 1, oz, ModBlocks.CEILING_LIGHT.defaultBlockState());
        GenUtil.set(level, ox - 3, sy - 1, oz - 3, ModBlocks.CEILING_LIGHT.defaultBlockState());

        // Hatch house on the surface with a Level 4 door, and a ladder down.
        GenUtil.box(level, ox + 2, sy - 1, oz + 2, ox + 4, sy + 2, oz + 4, concrete, air);
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(new BlockPos(ox + 3, sy, oz + 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 18);
        level.setBlock(new BlockPos(ox + 3, sy + 1, oz + 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 18);
        level.setBlock(new BlockPos(ox + 2, sy + 1, oz + 4), ModBlocks.KEYCARD_READER.defaultBlockState()
                .setValue(FacingBlock.FACING, Direction.SOUTH).setValue(KeycardReaderBlock.LEVEL, 4), 2);
        for (int y = floor + 1; y <= sy + 1; y++) {
            GenUtil.set(level, ox + 4, y, oz + 3, concrete);
            level.setBlock(new BlockPos(ox + 3, y, oz + 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 18);
        }

        // Beds, supplies and light.
        BlockState bed = Blocks.BED.green().defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
        for (int i = 0; i < 4; i++) {
            int z = oz - 3 + i * 2;
            level.setBlock(new BlockPos(ox - 3, floor + 1, z), bed.setValue(BedBlock.PART, BedPart.HEAD), 18);
            level.setBlock(new BlockPos(ox - 2, floor + 1, z), bed.setValue(BedBlock.PART, BedPart.FOOT), 18);
        }
        GenUtil.lootChest(level, new BlockPos(ox + 3, floor + 1, oz - 3), random, false);
        GenUtil.set(level, ox + 2, floor + 1, oz - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        GenUtil.set(level, ox + 1, floor + 1, oz - 3, Blocks.BARREL.defaultBlockState());
        GenUtil.set(level, ox, floor + 1, oz + 2, Blocks.LANTERN.defaultBlockState());

        int survivors = 1 + random.nextInt(4);
        Random r = new Random(random.nextLong());
        for (int i = 0; i < survivors; i++) {
            StaffSpawner.spawnSurvivor(level, new BlockPos(ox - 1 + i % 3, floor + 1, oz - 1 + i / 3), r);
        }
        return true;
    }
}
