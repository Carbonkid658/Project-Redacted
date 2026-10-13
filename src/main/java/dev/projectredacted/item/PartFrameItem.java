package dev.projectredacted.item;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.PartFrameScreen;
import dev.projectredacted.site.gen.PartFrames;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Part Frame (site generation design §7.1), creative only: click two opposite corners of a hand-built part to
 * frame it, then name it and set its kind in the screen. Use it in the air inside a frame to edit that frame.
 */
public class PartFrameItem extends Item {
    private static final Map<UUID, BlockPos> FIRST = new HashMap<>();

    public PartFrameItem(Properties properties) {
        super(properties);
    }

    public static BlockPos firstCorner(Player player) {
        return FIRST.get(player.getUUID());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!player.isCreative()) {
            Msg.send(player, "The Part Frame only works in creative mode.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().immutable();
        BlockPos first = FIRST.remove(player.getUUID());
        if (first == null) {
            PartFrames.Frame inside = PartFrames.at(pos);
            if (inside != null && player.isShiftKeyDown()) {
                Gui.open((ServerPlayer) player, new PartFrameScreen(inside));
                return InteractionResult.SUCCESS;
            }
            FIRST.put(player.getUUID(), pos);
            Msg.send(player, "Corner 1 set at " + pos.toShortString() + ". Now click the opposite corner (top of the ceiling).", ChatFormatting.AQUA);
            return InteractionResult.SUCCESS;
        }
        PartFrames.Frame frame = PartFrames.add(first, pos);
        Msg.send(player, "Framed " + frame.id + " (" + frame.size() + "). Name it and set its kind.", ChatFormatting.GREEN);
        Gui.open((ServerPlayer) player, new PartFrameScreen(frame));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown() && FIRST.remove(player.getUUID()) != null) {
            Msg.actionBar(player, "Corner cleared.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        PartFrames.Frame frame = PartFrames.at(player.blockPosition());
        if (frame == null) {
            Msg.send(player, "Stand inside a frame to edit it, or click two corners to frame a part. Sneak-click in the air to clear a corner.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        Gui.open((ServerPlayer) player, new PartFrameScreen(frame));
        return InteractionResult.SUCCESS;
    }
}
