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
        out.add(room("projectredacted:light_cell", 7, 7));
        out.add(entrance());
        // Plain halls and a stairwell to link things up (and for sites from before v4).
        out.add(hallStraight(5));
        out.add(hallStraight(9));
        out.add(hall("hall_corner", true, true, false, false));
        out.add(hall("hall_t", true, true, false, true));
        out.add(hall("hall_cross", true, true, true, true));
        out.add(stairwell());
        // v4 (§3, §4.1): area halls on the 5×5 grid, checkpoint, hub, spine and tunnel pieces.
        out.add(areaHall("area_hall_straight_5", 5, new boolean[]{true, false, true, false}, new int[][]{{4, 2, 1}, {0, 2, 3}}));
        out.add(areaHall("area_hall_straight_10", 10, new boolean[]{true, false, true, false}, new int[][]{{4, 2, 1}, {0, 2, 3}, {4, 7, 1}, {0, 7, 3}}));
        out.add(areaHall("area_hall_corner", 5, new boolean[]{true, true, false, false}, new int[][]{{2, 4, 2}, {0, 2, 3}}));
        out.add(areaHall("area_hall_t", 5, new boolean[]{true, true, false, true}, new int[][]{{2, 4, 2}}));
        out.add(areaHall("area_hall_cross", 5, new boolean[]{true, true, true, true}, new int[0][]));
        out.add(areaHall("area_hall_end", 5, new boolean[]{true, false, false, false}, new int[][]{{4, 2, 1}, {0, 2, 3}, {2, 4, 2}}));
        out.add(hub());
        out.add(gate("checkpoint_5x10", 10, new int[]{4}));
        out.add(shared("spine_straight_5x10", SitePart.TAG_SPINE, 10, new boolean[]{true, false, true, false}));
        out.add(shared("spine_t_5x5", SitePart.TAG_SPINE, 5, new boolean[]{true, true, true, false}));
        out.add(shared("spine_cross_5x5", SitePart.TAG_SPINE, 5, new boolean[]{true, true, true, true}));
        out.add(shared("tunnel_straight_5x15", SitePart.TAG_TUNNEL, 15, new boolean[]{true, false, true, false}));
        out.add(shared("tunnel_corner_5x5", SitePart.TAG_TUNNEL, 5, new boolean[]{true, true, false, false}));
        out.add(shared("tunnel_t_5x5", SitePart.TAG_TUNNEL, 5, new boolean[]{true, true, false, true}));
        out.add(shared("tunnel_cross_5x5", SitePart.TAG_TUNNEL, 5, new boolean[]{true, true, true, true}));
        return out;
    }

    /** The central hub: a 15×15 lobby with two hall arms on every side, on the 5×5 grid. */
    private static SitePart hub() {
        SitePart p = base("hub_15x15", SitePart.Kind.HUB, 15, 15);
        p.shape = new Shape("hall", null);
        for (int k : new int[]{2, 12}) {
            p.connectors.add(conn(k, 1, 0, Direction.NORTH, SitePart.HALL));
            p.connectors.add(conn(14, 1, k, Direction.EAST, SitePart.HALL));
            p.connectors.add(conn(k, 1, 14, Direction.SOUTH, SitePart.HALL));
            p.connectors.add(conn(0, 1, k, Direction.WEST, SitePart.HALL));
        }
        p.markers.add(new SitePart.Marker(new BlockPos(3, 1, 3), "staff_post"));
        return p;
    }

    /**
     * An area's checkpoint: hall openings north (outside) and south (inside) with a wall across, a door in it and a
     * keycard reader on both faces. Its reader levels come from the area behind it ({@link Areas#applyReaders}).
     */
    private static SitePart gate(String id, int length, int[] walls) {
        SitePart p = base(id, SitePart.Kind.GATE, 5, length);
        p.shape = new Shape("gate", null);
        p.connectors.add(conn(2, 1, 0, Direction.NORTH, SitePart.HALL));
        p.connectors.add(conn(2, 1, length - 1, Direction.SOUTH, SitePart.HALL));
        for (int z : walls) {
            p.markers.add(new SitePart.Marker(new BlockPos(2, 1, z), "gate_door"));
            p.readers.add(new BlockPos(1, 2, z));
            p.readers.add(new BlockPos(3, 2, z));
        }
        p.markers.add(new SitePart.Marker(new BlockPos(3, 1, 1), "staff_post"));
        return p;
    }

    /**
     * A hall piece for any area (tagged both main and side hall); it takes the area's floor when built. Openings
     * by side (north, east, south, west), and room door slots as {x, z, side}.
     */
    private static SitePart areaHall(String id, int length, boolean[] open, int[][] doors) {
        SitePart p = base(id, SitePart.Kind.HALLWAY, 5, length);
        p.shape = new Shape("hall", null);
        p.tags.add(SitePart.TAG_MAIN_HALL);
        p.tags.add(SitePart.TAG_SIDE_HALL);
        openings(p, length, open);
        for (int[] d : doors) p.connectors.add(conn(d[0], 1, d[1], SitePart.sideDir(d[2]), SitePart.DOOR));
        p.weight = id.endsWith("straight_5") ? 12 : id.endsWith("straight_10") ? 10 : id.endsWith("end") ? 3 : 5;
        return p;
    }

    /** A shared spine or tunnel piece. */
    private static SitePart shared(String id, String tag, int length, boolean[] open) {
        SitePart p = base(id, SitePart.Kind.HALLWAY, 5, length);
        p.shape = new Shape("hall", null);
        p.tags.add(tag);
        openings(p, length, open);
        p.weight = length > 5 ? 12 : 6;
        return p;
    }

    private static void openings(SitePart p, int length, boolean[] open) {
        int mid = Math.min(2, length / 2);
        if (open[0]) p.connectors.add(conn(2, 1, 0, Direction.NORTH, SitePart.HALL));
        if (open[1]) p.connectors.add(conn(4, 1, mid, Direction.EAST, SitePart.HALL));
        if (open[2]) p.connectors.add(conn(2, 1, length - 1, Direction.SOUTH, SitePart.HALL));
        if (open[3]) p.connectors.add(conn(0, 1, mid, Direction.WEST, SitePart.HALL));
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

    /** The floor an area's halls get (V6: every area has its own look). */
    private static BlockState floorFor(String area) {
        if (area == null) return ModBlocks.FACILITY_FLOOR.defaultBlockState();
        return (switch (area) {
            case Areas.ADMIN -> ModBlocks.OFFICE_TILE;
            case Areas.LIVING -> ModBlocks.SAGE_GREEN_TILE;
            case Areas.RESEARCH -> ModBlocks.BLUE_TILE;
            case Areas.CONTAINMENT -> ModBlocks.SECURITY_TILE;
            case Areas.ENTRANCE -> ModBlocks.FACILITY_TILE;
            default -> ModBlocks.FACILITY_FLOOR;
        }).defaultBlockState();
    }

    /** {@code area} is the area kind the part belongs to (for its look), or null. */
    public static RoomBuilder.Plan build(SitePart part, BlockPos origin, int rot, int used, long seed, String area) {
        return switch (part.shape.style()) {
            case "room" -> {
                RoomType type = RoomTypes.get(part.shape.roomType());
                Room r = roomFor(part, origin, rot, used, seed);
                yield type == null ? new RoomBuilder.Plan() : RoomBuilder.plan(r, type);
            }
            case "stair" -> corridor(part, origin, rot, used, true, area);
            case "gate" -> gate(part, origin, rot, used, area);
            default -> corridor(part, origin, rot, used, false, area);
        };
    }

    /** Hallways and stairwells: a plain shell with openings where something is joined. */
    private static RoomBuilder.Plan corridor(SitePart part, BlockPos origin, int rot, int used, boolean stair, String area) {
        RoomBuilder.Plan plan = new RoomBuilder.Plan();
        Box b = part.box(origin, rot);
        BlockState wall = ModBlocks.FACILITY_WALL.defaultBlockState(), floor = floorFor(area);
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

    /** A corridor shell with the gate's wall, door and readers across it. */
    private static RoomBuilder.Plan gate(SitePart part, BlockPos origin, int rot, int used, String area) {
        RoomBuilder.Plan shell = corridor(part, origin, rot, used, false, area);
        java.util.Map<Long, RoomBuilder.Placement> blocks = new java.util.LinkedHashMap<>();
        for (RoomBuilder.Placement p : shell.placements) blocks.put(p.pos().asLong(), p);
        BlockState wall = ModBlocks.FACILITY_WALL.defaultBlockState();
        BlockState door = ModBlocks.SLIDING_DOOR.defaultBlockState();
        Direction north = SitePart.rotate(Direction.NORTH, rot);
        List<RoomBuilder.Placement> doors = new ArrayList<>();
        boolean signed = false;
        for (SitePart.Marker m : part.markers) {
            if (!m.name().equals("gate_door")) continue;
            int z = m.pos().getZ();
            if (!signed) {
                // Area signs above the first door, on both faces; Areas fills in the text.
                signed = true;
                for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                    BlockPos sp = origin.offset(SitePart.rotate(new BlockPos(2, 3, z + face.getStepZ()), rot));
                    doors.add(new RoomBuilder.Placement(sp, Blocks.OAK_WALL_SIGN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, SitePart.rotate(face, rot)), false, false));
                }
            }
            for (int x = 1; x <= part.sx - 2; x++)
                for (int y = 1; y < H - 1; y++) {
                    BlockPos w = origin.offset(SitePart.rotate(new BlockPos(x, y, z), rot));
                    blocks.put(w.asLong(), new RoomBuilder.Placement(w, wall, false, true));
                }
            BlockPos lower = origin.offset(SitePart.rotate(m.pos(), rot));
            blocks.remove(lower.asLong());
            blocks.remove(lower.above().asLong());
            BlockState d = door.setValue(net.minecraft.world.level.block.DoorBlock.FACING, north).setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false);
            doors.add(new RoomBuilder.Placement(lower, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), true, true));
            doors.add(new RoomBuilder.Placement(lower.above(), d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), true, true));
        }
        // Readers face away from the wall: west one north, east one south (the levels are set when it's done).
        for (BlockPos r : part.readers) {
            BlockPos w = origin.offset(SitePart.rotate(r, rot));
            Direction face = r.getX() < part.sx / 2 ? Direction.NORTH : Direction.SOUTH;
            BlockState rs = ModBlocks.KEYCARD_READER.defaultBlockState().setValue(dev.projectredacted.block.FacingBlock.FACING, SitePart.rotate(face, rot));
            blocks.put(w.asLong(), new RoomBuilder.Placement(w, rs, false, true));
            shell.readers.add(w);
        }
        shell.placements.clear();
        shell.placements.addAll(blocks.values());
        shell.placements.addAll(doors);
        return shell;
    }

    /** Which way a placeholder gate's reader faces, in local coordinates (templates read it from the block). */
    public static Direction readerFacing(SitePart part, BlockPos local) {
        return local.getX() < part.sx / 2 ? Direction.NORTH : Direction.SOUTH;
    }

    private ProceduralParts() {}
}
