package dev.projectredacted.site.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One building block of a facility (site generation design §2): a room, a hallway piece, a stairwell or the
 * Entrance. Coordinates are local to the part's box, which starts at (0,0,0). Connectors are the doorways
 * where parts join, following the building standard (§3).
 */
public class SitePart {
    public static final int STOREY = 6;

    public enum Kind {
        ROOM, HALLWAY, STAIRWELL, ENTRANCE;

        public static Kind parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                return ROOM;
            }
        }

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    /** Connector types (§3.1). */
    public static final String DOOR = "door", HALL = "hall", STAIR_UP = "stair_up", STAIR_DOWN = "stair_down", SURFACE_EXIT = "surface_exit";

    /**
     * A doorway. {@code pos} is the doorway's bottom-middle block in the wall, {@code facing} points out of the
     * part. Stair connectors face up or down and use {@code top} to line up the landing.
     */
    public record Connector(BlockPos pos, Direction facing, String type, Direction top, String turnsInto) {
        public boolean vertical() {
            return facing.getAxis() == Direction.Axis.Y;
        }

        /** Whether a part's connector {@code other} can join this one. */
        public boolean joins(Connector other) {
            if (type.equals(SURFACE_EXIT) || other.type.equals(SURFACE_EXIT)) return false;
            if (vertical()) {
                return (type.equals(STAIR_UP) && other.type.equals(STAIR_DOWN)) || (type.equals(STAIR_DOWN) && other.type.equals(STAIR_UP));
            }
            return type.equals(other.type) && !other.vertical();
        }

        /** Width of the opening: 3 for halls and stair landings, 1 for doors. */
        public int width() {
            return type.equals(DOOR) ? 1 : 3;
        }

        public int height() {
            return type.equals(DOOR) ? 2 : 3;
        }
    }

    /** A room inside the part, in local coordinates (walls included). doorSide: 0 north, 1 east, 2 south, 3 west. */
    public record PartRoom(String type, int x1, int y1, int z1, int x2, int y2, int z2, int doorSide) {}

    /** A Data-mode structure block word (§3.2), e.g. raider_entry. */
    public record Marker(BlockPos pos, String name) {}

    public String id;
    public String kit = "default";
    public Kind kind = Kind.ROOM;
    /** For room parts whose whole box is one room. */
    public String roomType;
    public int weight = 10;
    public int sx, sy, sz;
    public final List<Connector> connectors = new ArrayList<>();
    public final List<PartRoom> rooms = new ArrayList<>();
    public final List<Marker> markers = new ArrayList<>();
    /** Blocks from the part's .nbt, or null for a procedural part. */
    public PartTemplate template;
    /** Set for parts the mod builds itself (the placeholder kit and fallbacks). */
    public ProceduralParts.Shape shape;
    /** Detected from the blocks (§3.2), in local coordinates; null when the part has none. */
    public BlockPos mainframe, terminal, cassie;
    public final List<BlockPos> readers = new ArrayList<>();

    public boolean procedural() {
        return template == null;
    }

    public int storeys() {
        return Math.max(1, (sy + STOREY - 1) / STOREY);
    }

    /** Every room type this part provides. */
    public List<String> roomTypes() {
        List<String> out = new ArrayList<>();
        if (roomType != null) out.add(roomType);
        for (PartRoom r : rooms) if (!out.contains(r.type())) out.add(r.type());
        return out;
    }

    public boolean has(String connectorType) {
        for (Connector c : connectors) if (c.type().equals(connectorType)) return true;
        return false;
    }

    // ---------------------------------------------------------------- rotation

    /** Turns a local position clockwise r times around the origin (same as vanilla structure rotation). */
    public static BlockPos rotate(BlockPos p, int r) {
        return switch (r & 3) {
            case 1 -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case 2 -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case 3 -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }

    public static Direction rotate(Direction d, int r) {
        if (d.getAxis() == Direction.Axis.Y) return d;
        for (int i = 0; i < (r & 3); i++) d = d.getClockWise();
        return d;
    }

    public static int side(Direction d) {
        return switch (d) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
    }

    public static Direction sideDir(int side) {
        return switch (side & 3) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    /** The world box this part covers when its local origin sits at {@code origin} turned r times. */
    public Box box(BlockPos origin, int r) {
        BlockPos a = origin.offset(rotate(BlockPos.ZERO, r)), b = origin.offset(rotate(new BlockPos(sx - 1, sy - 1, sz - 1), r));
        return Box.of(a, b);
    }

    /** Where local (0,0,0) must go so that the rotated box's minimum corner lands on {@code min}. */
    public BlockPos originFor(BlockPos min, int r) {
        Box atZero = box(BlockPos.ZERO, r);
        return min.offset(-atZero.x1(), -atZero.y1(), -atZero.z1());
    }

    public Connector worldConnector(int i, BlockPos origin, int r) {
        Connector c = connectors.get(i);
        return new Connector(origin.offset(rotate(c.pos(), r)), rotate(c.facing(), r), c.type(), rotate(c.top(), r), c.turnsInto());
    }

    @Override
    public String toString() {
        return kit + "/" + id;
    }
}
