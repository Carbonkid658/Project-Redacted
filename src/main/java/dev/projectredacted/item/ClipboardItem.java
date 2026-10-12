package dev.projectredacted.item;

import dev.projectredacted.gui.ClipboardGui;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Task assignment. Use it on a staff member to write them a target (room number, SCP number, checkpoint or
 * entrance). Use it in the air to see the site's roster. Assigning is handled in StaffEntity#mobInteract.
 */
public class ClipboardItem extends Item {
    public ClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.memberSite(player);
        if (site == null) {
            Msg.send(player, "You don't belong to a site.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        ClipboardGui.openRoster((ServerPlayer) player, site, 0);
        return InteractionResult.SUCCESS;
    }
}
