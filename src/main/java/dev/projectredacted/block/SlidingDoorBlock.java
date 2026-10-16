package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A door that slides sideways instead of swinging. Works like an iron door: redstone and keycard readers open
 * it, hands don't. Closed, the panel sits in the middle of the block; open, it slides toward the hinge side into
 * the wall, leaving a thin edge.
 */
public class SlidingDoorBlock extends DoorBlock {
    private static final Map<Direction, VoxelShape> CLOSED = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> OPEN_LEFT = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> OPEN_RIGHT = new EnumMap<>(Direction.class);

    static {
        // Shapes drawn for a door facing east (the model's unrotated frame), then turned like the blockstate turns the model.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int turns = switch (d) {
                case SOUTH -> 1;
                case WEST -> 2;
                case NORTH -> 3;
                default -> 0;
            };
            CLOSED.put(d, box(6.5, 0, 16, 9.5, turns));
            OPEN_LEFT.put(d, box(6.5, 0, 2, 9.5, turns));
            OPEN_RIGHT.put(d, box(6.5, 14, 16, 9.5, turns));
        }
    }

    /** A box from x1..x2 across and z1..z2 along, turned clockwise a quarter at a time. */
    private static VoxelShape box(double x1, double z1, double z2, double x2, int turns) {
        double ax = x1, az = z1, bx = x2, bz = z2;
        for (int i = 0; i < turns; i++) {
            double nax = 16 - az, naz = ax, nbx = 16 - bz, nbz = bx;
            ax = nax;
            az = naz;
            bx = nbx;
            bz = nbz;
        }
        return Shapes.box(Math.min(ax, bx) / 16, 0, Math.min(az, bz) / 16, Math.max(ax, bx) / 16, 1, Math.max(az, bz) / 16);
    }

    public SlidingDoorBlock(Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(OPEN)) return CLOSED.get(facing);
        return state.getValue(HINGE) == DoorHingeSide.LEFT ? OPEN_LEFT.get(facing) : OPEN_RIGHT.get(facing);
    }
}
