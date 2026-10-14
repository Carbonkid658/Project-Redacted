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
        ROOM, HALLWAY, STAIRWELL, ENTRANCE,
        /** The central hub areas open onto (v4 §4.1). */
        HUB,
        /** An area's checkpoint: the only way into it (v4 §3). */
        GATE;

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

    /** Hallway tags (v4 §5.1): an area's main and side halls, and the shared spine and tunnel pieces. */
    public static final String TAG_MAIN_HALL = "main_hall", TAG_SIDE_HALL = "side_hall", TAG_SPINE = "spine", TAG_TUNNEL = "tunnel";
    public static final List<String> TAGS = List.of(TAG_MAIN_HALL, TAG_SIDE_HALL, TAG_SPINE, TAG_TUNNEL);
    /** Largest build, so every room is one structure block save (V15). */
    public static final int MAX_SIZE = 48;
    /** Hall and checkpoint footprints are multiples of this (V7). */
    public static final int GRID = 5;

    /** Connector types (§3.1). */
    public static final String DOOR = "door", HALL = "hall", STAIR_UP = "stair_up", STAIR_DOWN = "stair_down", SURFACE_EXIT = "surface_exit";
    /** Companion links (v4 §5.5, §5.6): link/&lt;room type&gt;, link_optional/&lt;room type&gt;, link_exact/&lt;area&gt;/&lt;kind&gt;/&lt;name&gt;. */
    public static final String LINK = "link/", LINK_OPTIONAL = "link_optional/", LINK_EXACT = "link_exact/";

    /**
     * Where a link jigsaw leads: any variant of a room type ({@code roomType}), or one exact build ({@code exactId},
     * a save name like site:containment/hall/scp_173_corridor_a). Optional links are walled up when nothing fits.
     */
    public record Link(String roomType, String exactId, boolean required) {
        /** Whether {@code part} can sit on the other end of this link. */
        public boolean accepts(SitePart part) {
            if (exactId != null) return exactId.equals(part.id) || part.id != null && part.id.startsWith(exactId + "_w");
            return part.roomTypes().contains(roomType);
        }

        public static Link parse(String type) {
            if (type.startsWith(LINK_EXACT)) return new Link(null, "site:" + type.substring(LINK_EXACT.length()).toLowerCase(java.util.Locale.ROOT), true);
            if (type.startsWith(LINK_OPTIONAL)) return new Link(PartKit.normalize(type.substring(LINK_OPTIONAL.length())), null, false);
            if (type.startsWith(LINK)) return new Link(PartKit.normalize(type.substring(LINK.length())), null, true);
            return null;
        }
    }

    /**
     * A doorway. {@code pos} is the doorway's bottom-middle block in the wall, {@code facing} points out of the
     * part. Stair connectors face up or down and use {@code top} to line up the landing.
     */
    public record Connector(BlockPos pos, Direction facing, String type, Direction top, String turnsInto) {
        public boolean vertical() {
            return facing.getAxis() == Direction.Axis.Y;
        }

        /** A companion link (§5.5) rather than a doorway to the halls. */
        public boolean isLink() {
            return type.startsWith(LINK) || type.startsWith(LINK_OPTIONAL) || type.startsWith(LINK_EXACT);
        }

        public Link link() {
            return Link.parse(type);
        }

        /** Whether a part's connector {@code other} can join this one. Links only join links, and only when placing a group. */
        public boolean joins(Connector other) {
            if (type.equals(SURFACE_EXIT) || other.type.equals(SURFACE_EXIT) || isLink() || other.isLink()) return false;
            if (vertical()) {
                return (type.equals(STAIR_UP) && other.type.equals(STAIR_DOWN)) || (type.equals(STAIR_DOWN) && other.type.equals(STAIR_UP));
            }
            return type.equals(other.type) && !other.vertical();
        }

        /** Width of the opening: 3 for halls and stair landings, 1 for doors and links. */
        public int width() {
            return type.equals(DOOR) || isLink() ? 1 : 3;
        }

        public int height() {
            return type.equals(DOOR) || isLink() ? 2 : 3;
        }
    }

    /** A room inside the part, in local coordinates (walls included). doorSide: 0 north, 1 east, 2 south, 3 west. */
    public record PartRoom(String type, int x1, int y1, int z1, int x2, int y2, int z2, int doorSide, BlockPos key, String scp) {
        public PartRoom(String type, int x1, int y1, int z1, int x2, int y2, int z2, int doorSide) {
            this(type, x1, y1, z1, x2, y2, z2, doorSide, null, null);
        }
    }

    /** A Data-mode structure block word (§3.2), e.g. raider_entry. */
    public record Marker(BlockPos pos, String name) {}

    public String id;
    public String kit = "default";
    public Kind kind = Kind.ROOM;
    /** For room parts whose whole box is one room. */
    public String roomType;
    public int weight = 10;
    /** Area kind the part belongs to ("living"), or null for shared parts used anywhere (v4 §5.1). */
    public String area;
    /** For SCP-specific rooms (V11): the SCP it's built for, e.g. "scp_173"; null for any. */
    public String scp;
    /** Room Entrance Markers in the build (they become its door connectors). */
    public int entranceMarkers;
    /** main_hall, side_hall, spine, tunnel. */
    public final java.util.Set<String> tags = new java.util.TreeSet<>();
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
    /**
     * The blocks its rooms really cover (v4 §5.7: the spaces their key blocks close off, walls included), indexed
     * by {@link #cell}; null when the whole box counts. Other parts may nest into the rest of the box.
     */
    public java.util.BitSet solid;
    /** What shape detection found wrong (rooms that leak, two rooms in one space), for the piece checker. */
    public final List<String> shapeProblems = new ArrayList<>();

    public int cell(int x, int y, int z) {
        return x + sx * (y + sy * z);
    }

    /** Whether the local position is part of the build's real shape. */
    public boolean solidAt(BlockPos local) {
        if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0 || local.getX() >= sx || local.getY() >= sy || local.getZ() >= sz) return false;
        return solid == null || solid.get(cell(local.getX(), local.getY(), local.getZ()));
    }

    /** Whether the world position is part of the build's real shape when placed at {@code origin} turned r times. */
    public boolean solidAtWorld(BlockPos world, BlockPos origin, int r) {
        if (solid == null) return true;
        return solidAt(rotate(world.subtract(origin), (4 - (r & 3)) & 3));
    }

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

    public boolean tagged(String tag) {
        return tags.contains(tag);
    }

    /** Whether the part has a companion link (§5.5). */
    public boolean hasLinks() {
        for (Connector c : connectors) if (c.isLink()) return true;
        return false;
    }

    /**
     * A room with links but no doorway to the halls (§5.5): it only ever comes as another room's companion, so it
     * never shows up on its own.
     */
    public boolean companionOnly() {
        if (!hasLinks()) return false;
        for (Connector c : connectors) if (c.type().equals(HALL) || c.type().equals(DOOR)) return false;
        return true;
    }

    /** Whether one of this part's links accepts {@code other} (so {@code other} can be its companion). */
    public boolean linksTo(SitePart other) {
        for (Connector c : connectors) {
            Link l = c.isLink() ? c.link() : null;
            if (l != null && l.accepts(other)) return true;
        }
        return false;
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

    /** The ground kept clear in front of a surface exit so the Entrance stays a way in. */
    public static Box exitClearance(Connector c) {
        Direction along = c.facing().getClockWise();
        return Box.of(c.pos().relative(c.facing()).relative(along, -2).below(), c.pos().relative(c.facing(), 6).relative(along, 2).above(STOREY - 2));
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
