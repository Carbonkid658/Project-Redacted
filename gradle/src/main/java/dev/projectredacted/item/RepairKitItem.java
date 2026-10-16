package dev.projectredacted.item;

import dev.projectredacted.room.Room;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Supplies for repairing raid damage. Use inside a damaged room, or on a sabotaged Mainframe or C.A.S.S.I.E. */
public class RepairKitItem extends Item {
    public RepairKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Site site = SiteManager.at(level, player.blockPosition());
        Room room = site == null ? null : site.roomAt(player.blockPosition());
        if (room == null) {
            Msg.send(player, "Stand inside a damaged room to repair it.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        if (room.integrity >= 100) {
            Msg.send(player, room.displayName() + " is in good condition.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        room.integrity = Math.min(100, room.integrity + 50);
        if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
        Msg.send(player, room.displayName() + " repaired to " + room.integrity + "%.", ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }
}
