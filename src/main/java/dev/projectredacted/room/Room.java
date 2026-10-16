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
    public long seed;
    /** Added to the Circuit Breaker. */
    public boolean connected;
    public boolean built;
    /** 0-100. Raids damage rooms; engineers and Repair Kits fix them. A room at 0 stops working. */
    public int integrity = 100;
    /** Containment cells: the SCP number shown on the facility map. */
    public String scpLabel = "";
    /** Checkpoints and entrances get a name used by the clipboard. */
    public String name = "";
    /** The area it's in, as "living-2" (v4 §2), its number within the area, and the area's name for labels. */
    public String area;
    public int areaNo;
    public String areaName;
    /** Where NPCs enter: a Room Entrance Marker (v4 §5.4) and the side it points out of; null to guess from the box. */
    public BlockPos entrance;
    public int entranceSide = -1;
    /** The key block that makes this room (v4 §5.8); breaking it un-designates the room. Null for older rooms. */
    public BlockPos key;
    /**
     * Companion rooms (v4 §5.5): rooms placed together share a group, the number of the group's first room (0 for
     * none). An Observation Room linked to a cell watches that cell's room number (0 for none).
     */
    public int group;
    public int watches;
    /** Freeform rooms are hand-built by the player and only registered, never constructed. */
    public boolean freeform;

    public transient boolean poweredNow;
    public transient boolean wateredNow;
    public transient int scpCount;
    public transient int beds = -1;
    /** Containment cells: how much the walls slow a breakout (V27), worked out now and then. */
    public transient double wallFactor = 1;
    public transient long wallCheckedAt = -100000;

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
        return switch ((entrance != null && entranceSide >= 0 ? entranceSide : rotation) & 3) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    /** The block just outside the main door, where security stands guard. */
    public BlockPos outsideDoor() {
        Direction d = doorSide();
        if (entrance != null && entranceSide >= 0) return entrance.relative(d);
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

    /** "Containment-04", or "#12" for a room without an area. */
    public String label() {
        if (area == null || areaNo <= 0) return "#" + number;
        String name = areaName == null || areaName.isBlank() ? area : areaName;
        return name + "-" + (areaNo < 10 ? "0" : "") + areaNo;
    }

    /**
     * Finds a Room Entrance Marker inside the room (one with open space in front wins) and uses it as the room's
     * entrance. Leaves the entrance alone if there's none.
     */
    public void findEntrance(net.minecraft.world.level.BlockGetter level) {
        BlockPos best = null;
        Direction bestFacing = null;
        for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
            var s = level.getBlockState(p);
            if (!s.is(dev.projectredacted.registry.ModBlocks.ROOM_ENTRANCE_MARKER)) continue;
            Direction f = s.getValue(dev.projectredacted.block.RoomEntranceMarkerBlock.FACING);
            boolean open = level.getBlockState(p.relative(f)).getCollisionShape(level, p.relative(f)).isEmpty();
            if (best == null || open) {
                best = p.immutable();
                bestFacing = f;
                if (open) break;
            }
        }
        if (best == null) return;
        entrance = best;
        entranceSide = switch (bestFacing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
    }

    public String displayName() {
        RoomType t = typeDef();
        String base = t == null ? type : t.name;
        if (!name.isEmpty()) return name;
        if (!scpLabel.isEmpty()) return base + " " + label() + " (" + scpLabel + ")";
        return base + " " + label();
    }
}
