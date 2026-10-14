package dev.projectredacted.site.gen;

import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomShape;
import dev.projectredacted.site.Site;
import dev.projectredacted.util.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Hallways players build by hand (v4 §5.8, V25). An Area Hall Marker in one makes its floor space a hall of an
 * area: the space is flood-filled from the marker (stopping at the facility's own parts and rooms), and its walls
 * get door slots every few blocks, so auto-place can open a 1×2 doorway there and attach a room.
 */
public final class HandHalls {
    /** Door slots along the walls: one every this many blocks. */
    private static final int SLOT_SPACING = 3;

    /**
     * Makes the hallway around the marker at {@code pos} a hall of an area of {@code kind}. Returns why not, or
     * null when it's registered.
     */
    public static String register(ServerLevel level, Site site, BlockPos pos, String kind) {
        remove(site, pos);
        int size = ModConfig.get().roomScanSize;
        RoomShape.Grid world = RoomShape.world(level, pos, size);
        List<Box> taken = new ArrayList<>();
        for (PlacedPart p : site.parts) {
            Box b = p.removed ? null : p.box();
            if (b != null) taken.add(b);
        }
        for (Room r : site.rooms) taken.add(new Box(r.x1, r.y1, r.z1, r.x2, r.y2, r.z2));
        // The facility's own halls and rooms count as wall, so the fill stays in the hand-built part.
        RoomShape.Grid grid = new RoomShape.Grid() {
            @Override
            public RoomShape.Cell at(BlockPos p) {
                if (!p.equals(pos)) for (Box b : taken) if (b.contains(p)) return RoomShape.Cell.WALL;
                return world.at(p);
            }

            @Override
            public BlockState state(BlockPos p) {
                return world.state(p);
            }
        };
        RoomShape.Result space = RoomShape.fill(grid, List.of(pos));
        if (!space.closed()) {
            return space.leak() == null ? "There's no hallway around the marker."
                    : "This hallway isn't closed: it opens to the outside at " + space.leak().toShortString() + ".";
        }
        int[] box = space.box();
        if (!site.containsRoom(new Room(0, "", new BlockPos(box[0], box[1], box[2]), new BlockPos(box[3], box[4], box[5]), 0, 0))) {
            return "The hallway must be inside your claim.";
        }
        String key = areaFor(site, pos, kind);
        PlacedPart part = new PlacedPart();
        part.kit = "hand";
        part.id = "hand_hall";
        part.hand = box;
        part.marker = pos.immutable();
        part.origin = new BlockPos(box[0], box[1], box[2]);
        part.built = true;
        part.area = key;
        part.storey = Math.floorDiv(box[1] - SiteGen.baseFloor(site) + SitePart.STOREY / 2, SitePart.STOREY);
        if (Areas.hallLength(site, key, Areas.ROLE_MAIN_HALL, 0) == 0) {
            part.role = Areas.ROLE_MAIN_HALL;
        } else {
            SortedSet<Integer> sides = Areas.sideHalls(site, key);
            part.role = Areas.ROLE_SIDE_HALL;
            part.hall = sides.isEmpty() ? 1 : sides.last() + 1;
        }
        site.parts.add(part);
        int index = site.parts.size() - 1;
        for (Doorway d : slots(level, space, taken, index)) site.doorways.add(d);
        Areas.state(site, key);
        Areas.relabel(site);
        site.log("Hand-built hallway added to the " + Areas.displayName(site, key) + " area.");
        return null;
    }

    /** Takes the hallway made by the marker at {@code pos} off the books (its blocks stay). */
    public static boolean remove(Site site, BlockPos pos) {
        for (int i = 0; i < site.parts.size(); i++) {
            PlacedPart p = site.parts.get(i);
            if (p.removed || p.marker == null || !p.marker.equals(pos)) continue;
            p.removed = true;
            int index = i;
            site.doorways.removeIf(d -> d.part == index);
            return true;
        }
        return false;
    }

    /** The marker's area: the one it's in if it's of this kind, else the first of this kind, else a new one. */
    private static String areaFor(Site site, BlockPos pos, String kind) {
        String at = Areas.areaAt(site, pos);
        if (at != null && Areas.kindOf(at).equals(kind)) return at;
        List<String> have = Areas.instances(site, kind);
        return have.isEmpty() ? Areas.next(site, kind) : have.get(0);
    }

    /**
     * Door slots: wall blocks one above the floor, one block thick, with open ground (not the facility) behind
     * them, spaced out along the walls.
     */
    private static List<Doorway> slots(ServerLevel level, RoomShape.Result space, List<Box> taken, int part) {
        List<Doorway> out = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        List<Long> cells = new ArrayList<>(space.interior());
        cells.sort(Comparator.naturalOrder());
        for (long l : cells) {
            BlockPos c = BlockPos.of(l);
            // Floor level only: the cell below is floor, and there's headroom for a 2-high doorway.
            if (space.interior().contains(c.below().asLong()) || !space.interior().contains(c.above().asLong())) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos wall = c.relative(d), beyond = wall.relative(d);
                if (space.interior().contains(wall.asLong()) || space.interior().contains(wall.above().asLong())) continue;
                if (level.getBlockState(wall).isAir() || level.getBlockState(wall.above()).isAir()) continue;
                boolean blocked = false;
                for (Box b : taken) if (b.contains(beyond)) blocked = true;
                if (blocked) continue;
                boolean near = false;
                for (long u : used) {
                    BlockPos o = BlockPos.of(u);
                    if (o.getY() == wall.getY() && o.distManhattan(wall) < SLOT_SPACING) near = true;
                }
                if (near) continue;
                used.add(wall.asLong());
                Doorway door = new Doorway();
                door.pos = wall.immutable();
                door.facing = d;
                door.type = SitePart.DOOR;
                door.part = part;
                out.add(door);
            }
        }
        return out;
    }

    private HandHalls() {}
}
