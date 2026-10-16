package dev.projectredacted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BooleanSupplier;

/**
 * The Room Entrance Marker (site generation v4, §5.4): an invisible block in a room's doorway, its arrow pointing
 * out of the room. NPCs walk to it to enter the room, and the site generator joins the room to a hall here.
 * Like a barrier it shows (as an icon with an arrow of dust) only while a creative player holds one.
 */
public class RoomEntranceMarkerBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Set by the client: whether the local player is holding a marker. Always false on a server. */
    public static BooleanSupplier holding = () -> false;

    public RoomEntranceMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Placed like stairs: the arrow points the way the player is looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Only selectable (to break or replace it) while holding a marker, like the light block. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(asItem()) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Client only: the icon, and an arrow of dust pointing the way the marker faces. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!holding.getAsBoolean()) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), x, y, z, 0, 0, 0);
        Direction f = state.getValue(FACING), side = f.getClockWise();
        DustParticleOptions dust = new DustParticleOptions(0x33DD55, 0.8f);
        for (int i = 0; i <= 6; i++) {
            double d = 0.15 + i * 0.15;
            level.addParticle(dust, x + f.getStepX() * d, y - 0.45, z + f.getStepZ() * d, 0, 0, 0);
        }
        for (int i = 1; i <= 3; i++) {
            double back = 1.05 - i * 0.15, s = i * 0.15;
            level.addParticle(dust, x + f.getStepX() * back + side.getStepX() * s, y - 0.45, z + f.getStepZ() * back + side.getStepZ() * s, 0, 0, 0);
            level.addParticle(dust, x + f.getStepX() * back - side.getStepX() * s, y - 0.45, z + f.getStepZ() * back - side.getStepZ() * s, 0, 0, 0);
        }
    }
}
