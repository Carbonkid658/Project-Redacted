package dev.projectredacted.item;

import dev.projectredacted.gui.DesignatorGui;
import dev.projectredacted.room.DesignSession;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
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

/**
 * Room Configuration Device. Use on two blocks to mark the corners of an area (floor corner, then the opposite
 * ceiling corner), then use it in the air to pick a room type, preview it as ghost blocks, rotate, reroll and build.
 */
public class DesignatorItem extends Item {
    public DesignatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, context.getClickedPos());
        if (site == null || !site.canBuild(player)) {
            Msg.send(player, "You can only designate rooms inside a site where you have build rights.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        DesignSession session = DesignSession.get(player);
        BlockPos pos = context.getClickedPos().immutable();
        if (session.cornerA == null || session.cornerB != null) {
            session.cornerA = pos;
            session.cornerB = null;
            session.previewing = false;
            Msg.send(player, "Corner 1 set at " + pos.toShortString() + ". Now mark the opposite corner (the ceiling height counts).", ChatFormatting.AQUA);
        } else {
            session.cornerB = pos;
            Msg.send(player, "Corner 2 set. Area: " + session.describeSize() + ". Use the device in the air to choose a room.", ChatFormatting.AQUA);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.memberSite(player);
        if (site == null || !site.canBuild(player)) {
            Msg.send(player, "You need build rights in a site to use this.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        DesignSession session = DesignSession.get(player);
        if (session.cornerA == null || session.cornerB == null) {
            Msg.send(player, "Mark two corners first by using the device on blocks.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        if (session.typeId != null) DesignatorGui.openPreview((ServerPlayer) player, site, session);
        else DesignatorGui.openCategories((ServerPlayer) player, site, session);
        return InteractionResult.SUCCESS;
    }
}
