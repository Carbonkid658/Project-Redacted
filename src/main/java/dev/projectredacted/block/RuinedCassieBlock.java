package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.BlockPanels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A broken C.A.S.S.I.E. from an abandoned facility. Cheaper to repair than crafting a new one. Shares the
 * C.A.S.S.I.E. model, shape and rotation; it just doesn't join a site or open the console.
 */
public class RuinedCassieBlock extends CassieBlock {
    /** Default repair cost (the real cost is still being decided). */
    public static final int REPAIR_KITS = 2;

    public RuinedCassieBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // A ruined C.A.S.S.I.E. does nothing until it's repaired.
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new BlockPanels.Repair(pos, false));
        return InteractionResult.SUCCESS;
    }
}
