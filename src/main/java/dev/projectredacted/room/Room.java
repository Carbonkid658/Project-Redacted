package dev.projectredacted.room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** One room in a site: a box from (x1,y1,z1) floor corner to (x2,y2,z2) ceiling corner, walls included. */
public class Room {
    public int number;
    public String type;
    public int x1, y1, z1, x2, y2, z2;
    /** Which wall the main door is on: 0 north, 1 east, 2 south, 3 west. */
    public int rotation;
    /** Extra door walls as a bitmask (1 north, 2 east, 4 south, 8 west), used by generated layouts. */
    public int extraDoors;
    /** Exact door walls (same bits) for rooms built from site parts; -1 means the main door plus extraDoors. */
    public int doors = -1;
    /** Index of the site part this room belongs to, or -1 for rooms designated by hand. */
    public int part = -1;
    public long seed;
    /** Added to the Circuit Breaker. */
    public boolean connected;
    public boolean built;
    public int buildProgress;
    /** 0-100. Raids damage rooms; engineers and Repair Kits fix them. A room at 0 stops working. */
    public int integrity = 100;
    /** Containment cells: the SCP number shown on the facility map. */
    public String scpLabel = "";
    /** Checkpoints and entrances get a name used by the clipboard. */
    public String name = "";
    /** Freeform rooms are hand-built by the player and only registered, never constructed. */
    public boolean freeform;

    public transient boolean poweredNow;
    public transient boolean wateredNow;
    public transient int scpCount;
    public transient int beds = -1;

    public Room() {}

    public Room(int number, String type, BlockPos a, BlockPos b, int rotation, long seed) {
        this.number = number;
        this.type = type;
        this.x1 = Math.min(a.getX(), b.getX());
        this.y1 = Math.min(a.getY(), b.getY());
        this.z1 = Math.min(a.getZ(), b.getZ());
        this.x2 = Math.max(a.getX(), b.getX());
        this.y2 = Math.max(a.getY(), b.getY());
        this.z2 = Math.max(a.getZ(), b.getZ());
        this.rotation = rotation;
        this.seed = seed;
    }

    public RoomType typeDef() {
        return RoomTypes.get(type);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= x1 && pos.getX() <= x2 && pos.getY() >= y1 && pos.getY() <= y2 && pos.getZ() >= z1 && pos.getZ() <= z2;
    }

    public boolean interiorIntersects(Room other) {
        return x1 + 1 <= other.x2 - 1 && x2 - 1 >= other.x1 + 1
                && y1 + 1 <= other.y2 - 1 && y2 - 1 >= other.y1 + 1
                && z1 + 1 <= other.z2 - 1 && z2 - 1 >= other.z1 + 1;
    }

    public AABB box() {
        return new AABB(x1, y1, z1, x2 + 1, y2 + 1, z2 + 1);
    }

    public BlockPos center() {
        return new BlockPos((x1 + x2) / 2, y1 + 1, (z1 + z2) / 2);
    }

    public int floorArea() {
        return Math.max(1, (x2 - x1 - 1) * (z2 - z1 - 1));
    }

    public Direction doorSide() {
        return switch (rotation & 3) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    /** The block just outside the main door, where security stands guard. */
    public BlockPos outsideDoor() {
        Direction d = doorSide();
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        return switch (d) {
            case NORTH -> new BlockPos(cx, y1 + 1, z1 - 1);
            case SOUTH -> new BlockPos(cx, y1 + 1, z2 + 1);
            case EAST -> new BlockPos(x2 + 1, y1 + 1, cz);
            default -> new BlockPos(x1 - 1, y1 + 1, cz);
        };
    }

    public boolean working() {
        RoomType t = typeDef();
        if (!built || integrity <= 0 || t == null) return false;
        if (t.powerDraw > 0 && !poweredNow) return false;
        return t.waterDraw <= 0 || wateredNow;
    }

    public String displayName() {
        RoomType t = typeDef();
        String base = t == null ? type : t.name;
        if (!name.isEmpty()) return name;
        if (!scpLabel.isEmpty()) return base + " #" + number + " (" + scpLabel + ")";
        return base + " #" + number;
    }
}
