package dev.projectredacted.site.gen;

import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.room.Room;
import dev.projectredacted.room.RoomBuilder;
import dev.projectredacted.room.RoomType;
import dev.projectredacted.room.RoomTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The placeholder kit (design §2.8 and §10 step 1): parts the mod builds itself with {@link RoomBuilder}, sized
 * and doored to the building standard, so sites generate before hand-built parts arrive and any room type without
 * a part can still be auto-placed. Room widths are odd so every doorway sits exactly in the middle of its wall.
 */
public final class ProceduralParts {
    public record Shape(String style, String roomType) {}

    private static final int H = SitePart.STOREY;

    // ---------------------------------------------------------------- the kit

    public static List<SitePart> level0Kit() {
        List<SitePart> out = new ArrayList<>();
        out.add(room(RoomTypes.DIRECTORS_OFFICE, 11, 9));
        out.add(room(RoomTypes.BARRACKS, 9, 7));
        out.add(entrance());
        out.add(hallStraight(5));
        out.add(hallStraight(9));
        out.add(hall("hall_corner", true, true, false, false));
        out.add(hall("hall_t", true, true, false, true));
        out.add(hall("hall_cross", true, true, true, true));
        out.add(stairwell());
        return out;
    }

    /** One or two sizes of a room type, from its allowed side lengths. */
    public static List<SitePart> roomSizes(RoomType t) {
        List<SitePart> out = new ArrayList<>();
        int small = odd(Math.max(t.minSide, 7), t.maxSide);
        out.add(room(t.id, small, small));
        int large = odd(Math.min(t.maxSide, small + 6), t.maxSide);
        if (large > small) out.add(room(t.id, large, large));
        return out;
    }

    /** The largest odd number at most {@code max}, starting from {@code want}. */
    private static int odd(int want, int max) {
        int v = Math.min(want, Math.max(5, max));
        if (v % 2 == 0) v = v + 1 <= max ? v + 1 : v - 1;
        return Math.max(5, v);
    }

    public static SitePart room(String type, int sx, int sz) {
        SitePart p = base("room_" + type.substring(type.indexOf(':') + 1) + "_" + sx + "x" + sz, SitePart.Kind.ROOM, sx, sz);
        p.roomType = type;
        p.shape = new Shape("room", type);
        int cx = sx / 2, cz = sz / 2;
        p.connectors.add(conn(cx, 1, 0, Direction.NORTH, SitePart.DOOR));
        p.connectors.add(conn(sx - 1, 1, cz, Direction.EAST, SitePart.DOOR));
        p.connectors.add(conn(cx, 1, sz - 1, Direction.SOUTH, SitePart.DOOR));
        p.connectors.add(conn(0, 1, cz, Direction.WEST, SitePart.DOOR));
        p.rooms.add(new SitePart.PartRoom(type, 0, 0, 0, sx - 1, H - 1, sz - 1, 0));
        return p;
    }

    private static SitePart entrance() {
        SitePart p = base("entrance_9x7", SitePart.Kind.ENTRANCE, 9, 7);
        p.roomType = RoomTypes.ENTRANCE;
        p.shape = new Shape("room", RoomTypes.ENTRANCE);
        p.connectors.add(conn(4, 1, 0, Direction.NORTH, SitePart.SURFACE_EXIT));
        p.connectors.add(conn(4, 1, 6, Direction.SOUTH, SitePart.DOOR));
        p.connectors.add(conn(8, 1, 3, Direction.EAST, SitePart.DOOR));
        p.connectors.add(conn(0, 1, 3, Direction.WEST, SitePart.DOOR));
        p.rooms.add(new SitePart.PartRoom(RoomTypes.ENTRANCE, 0, 0, 0, 8, H - 1, 6, 0));
        p.markers.add(new SitePart.Marker(new BlockPos(4, 1, -1), "raider_entry"));
        return p;
    }

    private static SitePart hallStraight(int length) {
        SitePart p = base("hall_straight_" + length, SitePart.Kind.HALLWAY, 5, length);
        p.shape = new Shape("hall", null);
        p.connectors.add(conn(2, 1, 0, Direction.NORTH, SitePart.HALL));
        p.connectors.add(conn(2, 1, length - 1, Direction.SOUTH, SitePart.HALL));
        p.connectors.add(conn(4, 1, length / 2, Direction.EAST, SitePart.DOOR));
        p.connectors.add(conn(0, 1, length / 2, Direction.WEST, SitePart.DOOR));
        p.weight = length == 5 ? 8 : 12;
        return p;
    }

    private static SitePart hall(String id, boolean n, boolean e, boolean s, boolean w) {
        SitePart p = base(id, SitePart.Kind.HALLWAY, 5, 5);
        p.shape = new Shape("hall", null);
        if (n) p.connectors.add(conn(2, 1, 0, Direction.NORTH, SitePart.HALL));
        if (e) p.connectors.add(conn(4, 1, 2, Direction.EAST, SitePart.HALL));
        if (s) p.connectors.add(conn(2, 1, 4, Direction.SOUTH, SitePart.HALL));
        if (w) p.connectors.add(conn(0, 1, 2, Direction.WEST, SitePart.HALL));
        p.weight = 4;
        return p;
    }

    private static SitePart stairwell() {
        SitePart p = base("stairwell", SitePart.Kind.STAIRWELL, 5, 7);
        p.shape = new Shape("stair", null);
        p.connectors.add(conn(2, 1, 0, Direction.NORTH, SitePart.HALL));
        p.connectors.add(new SitePart.Connector(new BlockPos(2, H - 1, 5), Direction.UP, SitePart.STAIR_UP, Direction.NORTH, null));
        p.connectors.add(new SitePart.Connector(new BlockPos(2, 0, 5), Direction.DOWN, SitePart.STAIR_DOWN, Direction.NORTH, null));
        return p;
    }

    private static SitePart base(String id, SitePart.Kind kind, int sx, int sz) {
        SitePart p = new SitePart();
        p.id = id;
        p.kit = "placeholder";
        p.kind = kind;
        p.sx = sx;
        p.sy = H;
        p.sz = sz;
        return p;
    }

    private static SitePart.Connector conn(int x, int y, int z, Direction facing, String type) {
        return new SitePart.Connector(new BlockPos(x, y, z), facing, type, Direction.UP, null);
    }

    // ---------------------------------------------------------------- building

    /** The room record a procedural room part builds, with doors only where something is joined. */
    public static Room roomFor(SitePart part, BlockPos origin, int rot, int used, long seed) {
        Box b = part.box(origin, rot);
        int main = -1, extra = 0;
        for (int i = 0; i < part.connectors.size(); i++) {
            SitePart.Connector c = part.worldConnector(i, origin, rot);
            if (c.vertical()) continue;
            int side = SitePart.side(c.facing());
            boolean on = (used & (1 << i)) != 0;
            if (c.type().equals(SitePart.SURFACE_EXIT)) {
                main = side;
            } else if (on) {
                if (main < 0) main = side;
                else extra |= 1 << side;
            }
        }
        Room r = new Room(0, part.shape.roomType(), b.min(), b.max(), Math.max(0, main), seed);
        r.doors = (main >= 0 ? 1 << main : 0) | extra;
        return r;
    }

    public static RoomBuilder.Plan build(SitePart part, BlockPos origin, int rot, int used, long seed) {
        return switch (part.shape.style()) {
            case "room" -> {
                RoomType type = RoomTypes.get(part.shape.roomType());
                Room r = roomFor(part, origin, rot, used, seed);
                yield type == null ? new RoomBuilder.Plan() : RoomBuilder.plan(r, type);
            }
            case "stair" -> corridor(part, origin, rot, used, true);
            default -> corridor(part, origin, rot, used, false);
        };
    }

    /** Hallways and stairwells: a plain shell with openings where something is joined. */
    private static RoomBuilder.Plan corridor(SitePart part, BlockPos origin, int rot, int used, boolean stair) {
        RoomBuilder.Plan plan = new RoomBuilder.Plan();
        Box b = part.box(origin, rot);
        BlockState wall = ModBlocks.FACILITY_WALL.defaultBlockState(), floor = ModBlocks.FACILITY_FLOOR.defaultBlockState();
        BlockState light = ModBlocks.CEILING_LIGHT.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        java.util.Map<Long, BlockState> blocks = new java.util.LinkedHashMap<>();
        for (int x = b.x1(); x <= b.x2(); x++)
            for (int y = b.y1(); y <= b.y2(); y++)
                for (int z = b.z1(); z <= b.z2(); z++) {
                    boolean edgeX = x == b.x1() || x == b.x2(), edgeZ = z == b.z1() || z == b.z2();
                    BlockState s;
                    if (y == b.y1()) s = floor;
                    else if (y == b.y2()) s = !edgeX && !edgeZ && Math.floorMod(x + z, 4) == 0 ? light : wall;
                    else s = edgeX || edgeZ ? wall : air;
                    blocks.put(BlockPos.asLong(x, y, z), s);
                }
        for (int i = 0; i < part.connectors.size(); i++) {
            if ((used & (1 << i)) == 0) continue;
            SitePart.Connector c = part.worldConnector(i, origin, rot);
            if (c.vertical()) {
                Direction across = c.top().getClockWise();
                for (int k = -1; k <= 1; k++) blocks.put(c.pos().relative(across, k).asLong(), air);
                continue;
            }
            Direction along = c.facing().getClockWise();
            int half = c.width() / 2;
            for (int k = -half; k <= half; k++)
                for (int y = 0; y < c.height(); y++) blocks.put(c.pos().relative(along, k).above(y).asLong(), air);
        }
        List<RoomBuilder.Placement> ladders = new ArrayList<>();
        if (stair) {
            SitePart.Connector up = null;
            int upIndex = -1, downIndex = -1;
            for (int i = 0; i < part.connectors.size(); i++) {
                String t = part.connectors.get(i).type();
                if (t.equals(SitePart.STAIR_UP)) upIndex = i;
                if (t.equals(SitePart.STAIR_DOWN)) downIndex = i;
            }
            if (upIndex >= 0) {
                up = part.worldConnector(upIndex, origin, rot);
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, up.top());
                int from = (used & (1 << downIndex)) != 0 && downIndex >= 0 ? b.y1() : b.y1() + 1;
                int to = (used & (1 << upIndex)) != 0 ? b.y2() : b.y2() - 1;
                BlockPos column = new BlockPos(up.pos().getX(), 0, up.pos().getZ());
                for (int y = from; y <= to; y++) {
                    BlockPos lp = column.atY(y);
                    blocks.remove(lp.asLong());
                    ladders.add(new RoomBuilder.Placement(lp, ladder, false, true));
                }
            }
        }
        for (var e : blocks.entrySet()) {
            BlockState s = e.getValue();
            plan.placements.add(new RoomBuilder.Placement(BlockPos.of(e.getKey()), s, false, !s.isAir()));
        }
        plan.placements.addAll(ladders);
        return plan;
    }

    private ProceduralParts() {}
}
