package dev.projectredacted.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A decorative block built from boxes, turned to face the player. The boxes are drawn in pixels for a block facing
 * north (front at z = 0, back against the wall at z = 16), the same frame the block models use, and turned a
 * quarter at a time like the blockstate turns the model.
 */
public class ShapedBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Where the block hangs. WALL takes the facing from the clicked side, so plates stick to the wall you click. */
    public enum Mount { FLOOR, WALL, CEILING }

    /**
     * @param boxes    {x1, y1, z1, x2, y2, z2} in pixels; y may run to 32 for two-high blocks
     * @param collide  false for decals, wires and other things you walk through
     * @param particle shown now and then around the block (steam, sparks, drips), or null
     * @param sticky   slows entities down like a cobweb (barbed wire)
     */
    public record Spec(double[][] boxes, boolean collide, Mount mount, Supplier<ParticleOptions> particle, boolean sticky) {
        public static Spec of(double[][] boxes, boolean collide, Mount mount) {
            return new Spec(boxes, collide, mount, null, false);
        }
    }

    protected final Spec spec;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public ShapedBlock(Properties properties, Spec spec) {
        super(properties);
        this.spec = spec;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, shape(spec.boxes(), d, 0, 16));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** The boxes clipped to [yMin, yMax) and shifted down by yMin, turned to face d. */
    static VoxelShape shape(double[][] boxes, Direction d, double yMin, double yMax) {
        int turns = switch (d) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double y1 = Math.max(b[1], yMin) - yMin, y2 = Math.min(b[4], yMax) - yMin;
            if (y2 <= y1) continue;
            double ax = b[0], az = b[2], bx = b[3], bz = b[5];
            for (int i = 0; i < turns; i++) {
                double nax = 16 - az, naz = ax, nbx = 16 - bz, nbz = bx;
                ax = nax;
                az = naz;
                bx = nbx;
                bz = nbz;
            }
            double x1 = Math.min(ax, bx), x2 = Math.max(ax, bx), z1 = Math.min(az, bz), z2 = Math.max(az, bz);
            // Paper-thin decals still need something to click on.
            if (y2 - y1 < 0.5) y2 = Math.min(16, y1 + 0.5);
            if (x2 - x1 < 0.5) x2 = Math.min(16, x1 + 0.5);
            if (z2 - z1 < 0.5) {
                if (z2 >= 16) z1 = 15.5;
                else z2 = z1 + 0.5;
            }
            out = Shapes.or(out, Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16));
        }
        return out.isEmpty() ? Shapes.box(0, 0, 0, 1, 1 / 16.0, 1) : out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = spec.mount() == Mount.WALL && face.getAxis().isHorizontal()
                ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    protected VoxelShape shapeFor(BlockState state) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return spec.collide() ? shapeFor(state) : Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !spec.collide();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
        if (spec.sticky()) entity.makeStuckInBlock(state, new Vec3(0.4, 0.3, 0.4));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (spec.particle() == null || random.nextInt(3) != 0 || !showParticles(state)) return;
        level.addParticle(spec.particle().get(), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3 + random.nextDouble() * 0.6,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }

    protected boolean showParticles(BlockState state) {
        return true;
    }
}
