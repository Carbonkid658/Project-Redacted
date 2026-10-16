package dev.projectredacted.block;

import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.gen.Areas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Emergency Bulkhead: a shutter housing built into a corridor ceiling. When it closes, a steel shutter drops
 * down from it to the floor (up to {@link #MAX_DROP} blocks) and seals the corridor; when it opens, the shutter
 * goes back up. It closes while it gets a redstone signal, and during a lockdown of the area nearest to it
 * (a breach locks its area down). {@code FACING} is the way the corridor runs, so the shutter goes across it.
 */
public class EmergencyBulkheadBlock extends FacingBlock {
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int MAX_DROP = 8;

    public EmergencyBulkheadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CLOSED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CLOSED, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level.isClientSide() || old.is(this)) return;
        Site site = SiteManager.at(level, pos);
        if (site != null && !site.bulkheads.contains(pos)) site.bulkheads.add(pos.immutable());
        update((ServerLevel) level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) {
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            state = state.setValue(POWERED, powered);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            update((ServerLevel) level, pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (level.getBlockState(pos).is(this)) return;
        raise(level, pos);
        Site site = SiteManager.at(level, pos);
        if (site != null) site.bulkheads.remove(pos);
    }

    /** Whether a lockdown covers this bulkhead: the area of the room nearest to it is locked down. */
    public static boolean lockedDown(Site site, BlockPos pos) {
        if (site == null || site.lockdown.isEmpty()) return false;
        dev.projectredacted.room.Room nearest = null;
        double best = Double.MAX_VALUE;
        for (var r : site.rooms) {
            if (r.area == null) continue;
            double d = r.center().distSqr(pos);
            if (d < best) {
                best = d;
                nearest = r;
            }
        }
        return nearest != null && site.lockdown.contains(nearest.area);
    }

    /** Opens or closes the bulkhead at {@code pos} to match its signal and the site's lockdowns. */
    public static void update(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EmergencyBulkheadBlock)) return;
        boolean close = state.getValue(POWERED) || lockedDown(SiteManager.at(level, pos), pos);
        if (close == state.getValue(CLOSED)) return;
        level.setBlock(pos, state.setValue(CLOSED, close), Block.UPDATE_ALL);
        if (close) drop(level, pos, state.getValue(FACING));
        else raise(level, pos);
        level.playSound(null, pos, close ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1f, close ? 0.5f : 0.7f);
    }

    /** Every bulkhead in the site, after a lockdown starts or ends. */
    public static void updateAll(ServerLevel level, Site site) {
        for (BlockPos p : new java.util.ArrayList<>(site.bulkheads)) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof EmergencyBulkheadBlock) update(level, p, s);
            else site.bulkheads.remove(p);
        }
    }

    private static void drop(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState panel = ModBlocks.BULKHEAD_SHUTTER.defaultBlockState().setValue(BulkheadShutterBlock.FACING, facing);
        BlockPos p = pos.below();
        for (int i = 0; i < MAX_DROP; i++, p = p.below()) {
            BlockState s = level.getBlockState(p);
            if (!s.canBeReplaced() && !s.is(ModBlocks.BULKHEAD_SHUTTER)) break;
            if (!s.isAir() && !s.is(ModBlocks.BULKHEAD_SHUTTER)) level.destroyBlock(p, true);
            level.setBlock(p, panel, Block.UPDATE_ALL);
        }
    }

    private static void raise(ServerLevel level, BlockPos pos) {
        BlockPos p = pos.below();
        for (int i = 0; i < MAX_DROP && level.getBlockState(p).is(ModBlocks.BULKHEAD_SHUTTER); i++, p = p.below()) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
