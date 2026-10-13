package dev.projectredacted.site;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/**
 * Builds the Level 0 facility: an Entrance, a Barracks with 4 beds and the Director's Office, laid out for the
 * chosen site type. The Mainframe and Terminal then move into the Director's Office.
 */
public final class FacilityGenerator {
    private static final int OFFICE_HALF_X = 5, OFFICE_HALF_Z = 4, ROOM_HEIGHT = 5;

    public static void generateLevel0(ServerLevel level, Site site) {
        if (site.anchor == null) site.anchor = site.mainframe;
        BlockPos m = site.anchor;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m.getX(), m.getZ()) - 1;
        long seed = new Random().nextLong();

        int officeFloor = switch (site.type) {
            case UNDERGROUND -> surface - 20 - 1;
            default -> surface;
        };
        Room office = room(site, RoomTypes.DIRECTORS_OFFICE, m.getX() - OFFICE_HALF_X, officeFloor, m.getZ() - OFFICE_HALF_Z,
                m.getX() + OFFICE_HALF_X, officeFloor + ROOM_HEIGHT, m.getZ() + OFFICE_HALF_Z, 2, seed);

        Room barracks;
        Room entrance;
        switch (site.type) {
            case SURFACE_TALL -> {
                // Barracks stacked above the office, reached by a ladder in the corner.
                barracks = room(site, RoomTypes.BARRACKS, office.x1, officeFloor + ROOM_HEIGHT, office.z1,
                        office.x2, officeFloor + ROOM_HEIGHT * 2, office.z2, 2, seed + 1);
                entrance = room(site, RoomTypes.ENTRANCE, m.getX() - 4, officeFloor, office.z2, m.getX() + 4, officeFloor + ROOM_HEIGHT, office.z2 + 6, 2, seed + 2);
                entrance.extraDoors = 1;
            }
            case UNDERGROUND -> {
                barracks = room(site, RoomTypes.BARRACKS, m.getX() - 4, officeFloor, office.z1 - 6, m.getX() + 4, officeFloor + ROOM_HEIGHT, office.z1, 2, seed + 1);
                office.extraDoors |= 1;
                int tunnelStart = office.z2 + 1;
                int entranceFloor = officeFloor + 20;
                entrance = room(site, RoomTypes.ENTRANCE, m.getX() - 4, entranceFloor, tunnelStart + 20, m.getX() + 4, entranceFloor + ROOM_HEIGHT, tunnelStart + 26, 2, seed + 2);
                entrance.extraDoors = 1;
            }
            default -> {
                barracks = room(site, RoomTypes.BARRACKS, m.getX() - 4, officeFloor, office.z1 - 6, m.getX() + 4, officeFloor + ROOM_HEIGHT, office.z1, 2, seed + 1);
                office.extraDoors |= 1;
                entrance = room(site, RoomTypes.ENTRANCE, m.getX() - 4, officeFloor, office.z2, m.getX() + 4, officeFloor + ROOM_HEIGHT, office.z2 + 6, 2, seed + 2);
                entrance.extraDoors = 1;
            }
        }

        // Build everything right away (this is the free starting facility).
        for (Room r : new Room[]{office, barracks, entrance}) {
            RoomBuilder.Plan plan = RoomBuilder.plan(r, r.typeDef());
            for (RoomBuilder.Placement p : plan.placements) Construction.place(level, p);
            for (BlockPos rp : plan.readers) SiteManager.addReader(rp);
            r.built = true;
            r.buildProgress = plan.placements.size();
            if (r == office) moveCoreInto(level, site, plan);
        }
        if (site.type == SiteType.UNDERGROUND) tunnel(level, m.getX(), office.z2 + 1, officeFloor, 20);
        if (site.type == SiteType.SURFACE_TALL) ladder(level, office.x2 - 1, office.z2 - 1, officeFloor + 1, officeFloor + ROOM_HEIGHT + 1);
        site.anchor = site.anchor == null ? m : site.anchor;
        site.generated = true;
        site.log("Level 0 facility generated (" + site.type.displayName + ").");
    }

    private static Room room(Site site, String type, int x1, int y1, int z1, int x2, int y2, int z2, int rotation, long seed) {
        Room r = new Room(site.nextRoomNumber++, type, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), rotation, seed);
        RoomPlanner.name(site, r, r.typeDef());
        site.rooms.add(r);
        return r;
    }

    /** Moves the Mainframe and Terminal into the Director's Office. */
    private static void moveCoreInto(ServerLevel level, Site site, RoomBuilder.Plan plan) {
        BlockPos oldMain = site.mainframe;
        if (plan.mainframeSpot != null && !plan.mainframeSpot.equals(oldMain)) {
            if (level.getBlockState(oldMain).is(ModBlocks.MAINFRAME)) level.setBlock(oldMain, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(plan.mainframeSpot, ModBlocks.MAINFRAME.defaultBlockState().setValue(FacingBlock.FACING, Direction.NORTH), 3);
            site.mainframe = plan.mainframeSpot;
        }
        if (plan.terminalSpot != null) {
            if (site.terminal != null && level.getBlockState(site.terminal).is(ModBlocks.SITE_DIRECTOR_COMPUTER)) {
                level.setBlock(site.terminal, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(plan.terminalSpot, ModBlocks.SITE_DIRECTOR_COMPUTER.defaultBlockState().setValue(FacingBlock.FACING, Direction.SOUTH), 3);
            site.terminal = plan.terminalSpot;
        }
    }

    /** A 3-wide stair tunnel from the office's south door up to the surface entrance. */
    private static void tunnel(ServerLevel level, int cx, int startZ, int bottomFloor, int rise) {
        BlockState wall = ModBlocks.FACILITY_WALL.defaultBlockState();
        BlockState step = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int i = 0; i < rise; i++) {
            int z = startZ + i;
            int floor = bottomFloor + i + 1;
            for (int x = cx - 2; x <= cx + 2; x++) {
                boolean side = x == cx - 2 || x == cx + 2;
                level.setBlock(new BlockPos(x, floor - 1, z), wall, 3);
                for (int y = floor; y <= floor + 3; y++) {
                    BlockState s = side || y == floor + 3 ? (y == floor + 3 && x == cx && i % 4 == 0 ? ModBlocks.CEILING_LIGHT.defaultBlockState() : wall)
                            : (y == floor ? step : Blocks.AIR.defaultBlockState());
                    level.setBlock(new BlockPos(x, y, z), s, 3);
                }
            }
        }
    }

    private static void ladder(ServerLevel level, int x, int z, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 18);
        }
    }

    private FacilityGenerator() {}
}
