package dev.projectredacted.item;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.TerminalScreen;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.site.Upgrade;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Portable Tablet: the terminal on the go, unlocked by the Portable Tablet upgrade. */
public class TabletItem extends Item {
    public TabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.memberSite(player);
        if (site == null || !site.canUseTerminal(player)) {
            Msg.send(player, "No site link. ACCESS DENIED.", ChatFormatting.RED);
        } else if (!site.hasUpgrade(Upgrade.PORTABLE_TABLET)) {
            Msg.send(player, "Your site hasn't unlocked the Portable Tablet upgrade.", ChatFormatting.RED);
        } else if (!site.mainframeOnline()) {
            Msg.send(player, "Connection lost: the Mainframe is offline.", ChatFormatting.RED);
        } else {
            Gui.open((ServerPlayer) player, new TerminalScreen(site, null));
        }
        return InteractionResult.SUCCESS;
    }
}
