package dev.projectredacted.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * Pipes, ducts and cable trays: a centred box that runs along the axis you place it on. The box is given for the
 * vertical axis as {half-width x, half-width z} in pixels.
 */
public class AxisShapedBlock extends RotatedPillarBlock {
    private final VoxelShape y, x, z;
    private final Supplier<ParticleOptions> particle;

    public AxisShapedBlock(Properties properties, double halfX, double halfZ, Supplier<ParticleOptions> particle) {
        super(properties);
        double ax = halfX / 16, az = halfZ / 16;
        this.y = Shapes.box(0.5 - ax, 0, 0.5 - az, 0.5 + ax, 1, 0.5 + az);
        this.z = Shapes.box(0.5 - ax, 0.5 - az, 0, 0.5 + ax, 0.5 + az, 1);
        this.x = Shapes.box(0, 0.5 - az, 0.5 - ax, 1, 0.5 + az, 0.5 + ax);
        this.particle = particle;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = state.getValue(AXIS);
        return axis == Direction.Axis.Y ? y : axis == Direction.Axis.X ? x : z;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (particle == null || random.nextInt(4) != 0) return;
        level.addParticle(particle.get(), pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 0, 0, 0);
    }
}
