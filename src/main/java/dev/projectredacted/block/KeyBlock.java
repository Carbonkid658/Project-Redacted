package dev.projectredacted.block;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.RoomPanelScreen;
import dev.projectredacted.room.Room;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A key block (site generation v4 §5.8): placed inside a closed space, it makes that space its room.
 * Right-clicking it opens its room's panel. Some stand for a bed as well (the Bunk Bed).
 */
public class KeyBlock extends FacingBlock {
    /** Counts as one bed for housing, like a real bed. */
    public final boolean bed;

    public KeyBlock(Properties properties, boolean bed) {
        super(properties);
        this.bed = bed;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, pos);
        Room room = site == null ? null : site.roomWithKey(pos);
        if (room == null && site != null) room = site.roomAt(pos);
        if (room == null) {
            Msg.actionBar(player, "Not a room yet. Wall it in, with a floor and a roof, then place this again.", ChatFormatting.GRAY);
        } else if (!site.isMember(player)) {
            Msg.actionBar(player, room.displayName(), ChatFormatting.GRAY);
        } else {
            Gui.open((ServerPlayer) player, new RoomPanelScreen(site, room.number, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
