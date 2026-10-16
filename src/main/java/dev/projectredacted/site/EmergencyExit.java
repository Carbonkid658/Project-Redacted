package dev.projectredacted.site;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.block.KeycardReaderBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.site.gen.SiteGen;
import dev.projectredacted.site.gen.Wings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Emergency Exit rooms (design §4A.7, D12): when one finishes, a ladder shaft goes from its floor up to a hatch
 * in the surface (or in its own ceiling, for a room on the surface). An iron trapdoor closes it; the reader
 * outside wants A Wing clearance, the one inside lets any staff out.
 */
public final class EmergencyExit {
    public static void build(ServerLevel level, Site site, Room room) {
        int x = (room.x1 + room.x2) / 2, z = room.z1 + 1;
        int ground = SiteGen.height(level, x, z);
        // A room on the surface gets its hatch in the ceiling; a buried one gets a shaft up through the ground.
        int top = ground <= room.y2 ? room.y2 : ground;
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = room.y1 + 1; y < top; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (y >= room.y2) {
                // Above the room the shaft needs walls of its own.
                for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH}) {
                    BlockPos side = p.relative(d);
                    if (!level.getBlockState(side).isSolidRender()) level.setBlock(side, wall, 3);
                }
            } else if (!level.getBlockState(p.north()).isSolidRender()) {
                level.setBlock(p.north(), wall, 3);
            }
            level.setBlock(p, ladder, 3);
        }
        BlockPos hatch = new BlockPos(x, top, z);
        level.setBlock(hatch, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.FACING, Direction.SOUTH), 3);
        int outside = Math.max(1, Math.min(5, Wings.wingClearance(site, "A")));
        BlockPos outer = hatch.east().above();
        if (!level.getBlockState(outer.below()).isSolidRender()) level.setBlock(outer.below(), wall, 3);
        level.setBlock(outer, ModBlocks.KEYCARD_READER.defaultBlockState().setValue(FacingBlock.FACING, Direction.EAST).setValue(KeycardReaderBlock.LEVEL, outside), 3);
        BlockPos inner = hatch.east().below();
        level.setBlock(inner, ModBlocks.KEYCARD_READER.defaultBlockState().setValue(FacingBlock.FACING, Direction.WEST).setValue(KeycardReaderBlock.LEVEL, 1), 3);
        SiteManager.addReader(outer);
        SiteManager.addReader(inner);
        if (!site.hatchReaders.contains(inner)) site.hatchReaders.add(inner);
        DoorControl.setFromBelow(inner);
        site.log("Emergency Exit " + room.label() + " has a hatch at " + hatch.toShortString() + ".");
    }

    private EmergencyExit() {}
}
