package dev.projectredacted.world;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.room.RoomTypes;
import dev.projectredacted.site.SiteManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * An old, ruined SCP site: a crumbling office with a dead Mainframe (and sometimes a broken C.A.S.S.I.E.),
 * a barracks, an entrance and an empty Light Cell. Players can repair the Mainframe and move in.
 * SCPs will spawn in these once SCP content exists.
 */
public class RuinedSiteFeature implements Feature {
    public static final MapCodec<RuinedSiteFeature> CODEC = MapCodec.unit(RuinedSiteFeature::new);

    @Override
    public MapCodec<RuinedSiteFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos o) {
        int ox = o.getX(), oz = o.getZ(), floor = o.getY() - 1;
        long seed = random.nextLong();

        Room office = new Room(0, RoomTypes.DIRECTORS_OFFICE, new BlockPos(ox - 5, floor, oz - 4), new BlockPos(ox + 5, floor + 5, oz + 4), 2, seed);
        office.extraDoors = 1 | 2;
        Room barracks = new Room(0, RoomTypes.BARRACKS, new BlockPos(ox - 4, floor, oz - 10), new BlockPos(ox + 4, floor + 5, oz - 4), 2, seed + 1);
        Room entrance = new Room(0, RoomTypes.ENTRANCE, new BlockPos(ox - 4, floor, oz + 4), new BlockPos(ox + 4, floor + 5, oz + 10), 2, seed + 2);
        entrance.extraDoors = 1;
        Room cell = new Room(0, "projectredacted:light_cell", new BlockPos(ox + 5, floor, oz - 3), new BlockPos(ox + 11, floor + 5, oz + 3), 3, seed + 3);

        SiteManager.RuinRecord record = new SiteManager.RuinRecord();
        BlockPos mainframeAt = null;
        for (Room room : new Room[]{office, barracks, entrance, cell}) {
            RoomBuilder.Plan plan = RoomBuilder.plan(room, room.typeDef());
            for (RoomBuilder.Placement p : plan.placements) {
                BlockState s = p.state();
                if (p.structural() && !s.isAir()) {
                    int roll = random.nextInt(100);
                    if (roll < 15) continue; // crumbled away
                    if (roll < 25 && p.pos().getY() > floor) s = Blocks.COBBLESTONE.defaultBlockState();
                    else if (roll < 30 && p.pos().getY() > floor + 1) s = Blocks.COBWEB.defaultBlockState();
                    else if (roll < 34 && p.pos().getY() == floor) s = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
                level.setBlock(p.pos(), s, p.multipart() ? RoomBuilder.MULTIPART_FLAGS : 2);
            }
            if (room == office && plan.mainframeSpot != null) {
                mainframeAt = plan.mainframeSpot;
                level.setBlock(mainframeAt, ModBlocks.RUINED_MAINFRAME.defaultBlockState().setValue(FacingBlock.FACING, Direction.SOUTH), 2);
                if (plan.terminalSpot != null && random.nextInt(3) == 0) {
                    level.setBlock(plan.terminalSpot, ModBlocks.RUINED_CASSIE.defaultBlockState().setValue(FacingBlock.FACING, Direction.SOUTH), 2);
                }
            }
            record.rooms.add(room);
        }
        GenUtil.lootChest(level, new BlockPos(ox + 4, floor + 1, oz - 3), random, true);
        if (mainframeAt == null) return false;
        record.mainframe = mainframeAt;
        SiteManager.queueRuin(record);
        return true;
    }
}
