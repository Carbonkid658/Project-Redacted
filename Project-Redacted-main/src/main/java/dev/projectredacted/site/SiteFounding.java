package dev.projectredacted.site;

import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.OneOffScreens;
import dev.projectredacted.registry.ModBlocks;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.room.Room;
import dev.projectredacted.util.Cost;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Founding a site by placing a Mainframe (or repairing a ruined one), and moving into old ruined facilities. */
public final class SiteFounding {
    public static void onMainframePlaced(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            Msg.send(player, "Sites can only be founded in the Overworld.", ChatFormatting.RED);
            return;
        }
        Site own = SiteManager.directedBy(player.getUUID());
        Site here = SiteManager.at(pos);
        if (own != null) {
            if (here == own && !level.getBlockState(own.mainframe).is(ModBlocks.MAINFRAME)) {
                own.mainframe = pos.immutable();
                own.mainframeSabotaged = false;
                own.mainframePresent = true;
                Msg.send(player, "Mainframe rebuilt. Your site is running again.", ChatFormatting.GREEN);
            } else {
                Msg.send(player, "You already direct a site. This Mainframe isn't linked to anything.", ChatFormatting.YELLOW);
            }
            return;
        }
        if (here != null) {
            Msg.send(player, "This land is part of " + here.directorName + "'s site.", ChatFormatting.RED);
            return;
        }
        if (overlapsAnotherClaim(pos)) {
            Msg.send(player, "Too close to another site. A new site needs 5x5 chunks of free land.", ChatFormatting.RED);
            return;
        }
        Site site = found(player, pos);
        SiteManager.RuinRecord ruin = ruinInClaim(site);
        Gui.open(player, ruin != null ? new OneOffScreens.OldFacility(site, ruin) : new OneOffScreens.SiteFounded(site));
    }

    public static boolean repairRuinedMainframe(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            Msg.send(player, "Sites can only be founded in the Overworld.", ChatFormatting.RED);
            return false;
        }
        Site own = SiteManager.directedBy(player.getUUID());
        if (own != null) {
            if (!own.contains(pos)) {
                Msg.send(player, "You already direct a site elsewhere.", ChatFormatting.YELLOW);
                return false;
            }
            replaceRuined(level, pos);
            if (own.mainframe != null && level.getBlockState(own.mainframe).is(ModBlocks.MAINFRAME) && !own.mainframe.equals(pos)) {
                Msg.send(player, "Repaired. It's a spare: your site already has a Mainframe.", ChatFormatting.GRAY);
            } else {
                own.mainframe = pos.immutable();
                own.mainframeSabotaged = false;
                Msg.send(player, "Mainframe repaired and linked to your site.", ChatFormatting.GREEN);
            }
            return true;
        }
        if (SiteManager.at(pos) != null || overlapsAnotherClaim(pos)) {
            Msg.send(player, "Another site already claims this area.", ChatFormatting.RED);
            return false;
        }
        replaceRuined(level, pos);
        Site site = found(player, pos);
        for (SiteManager.RuinRecord r : SiteManager.ruins()) {
            if (!r.claimed && r.mainframe.equals(pos)) {
                adoptRooms(site, r);
                Msg.send(player, "You've moved into the old facility. Its rooms are damaged: repair them with Repair Kits or Engineers.", ChatFormatting.AQUA);
            }
        }
        return true;
    }

    private static void replaceRuined(ServerLevel level, BlockPos pos) {
        BlockState old = level.getBlockState(pos);
        BlockState fresh = ModBlocks.MAINFRAME.defaultBlockState();
        if (old.hasProperty(FacingBlock.FACING)) fresh = fresh.setValue(FacingBlock.FACING, old.getValue(FacingBlock.FACING));
        level.setBlock(pos, fresh, 3);
    }

    private static Site found(ServerPlayer player, BlockPos pos) {
        Site site = new Site();
        site.id = UUID.randomUUID().toString();
        site.director = player.getUUID();
        site.directorName = player.getName().getString();
        site.mainframe = pos.immutable();
        site.anchor = pos.immutable();
        SiteManager.add(site);
        site.log("Site founded by " + site.directorName + ".");
        // Founding a site grants a Level 4 keycard, which opens Foundation Bunkers.
        Cost.give(player, new ItemStack(ModItems.KEYCARD_4));
        Msg.send(player, site.name() + " founded. Welcome, Director " + site.directorName + ".", ChatFormatting.GOLD);
        return site;
    }

    private static boolean overlapsAnotherClaim(BlockPos pos) {
        int size = 5;
        int minX = (pos.getX() >> 4) - (size - 1) / 2, minZ = (pos.getZ() >> 4) - (size - 1) / 2;
        for (Site s : SiteManager.sites()) {
            int max = 8; // leave room for the other site to grow to Level 3
            int sMinX = (s.center().getX() >> 4) - (max - 1) / 2, sMinZ = (s.center().getZ() >> 4) - (max - 1) / 2;
            boolean overlapX = minX <= sMinX + max - 1 && sMinX <= minX + size - 1;
            boolean overlapZ = minZ <= sMinZ + max - 1 && sMinZ <= minZ + size - 1;
            if (overlapX && overlapZ) return true;
        }
        return false;
    }

    private static SiteManager.RuinRecord ruinInClaim(Site site) {
        for (SiteManager.RuinRecord r : SiteManager.ruins()) {
            if (!r.claimed && site.contains(r.mainframe)) return r;
        }
        return null;
    }

    public static void adoptRuin(ServerLevel level, Site site, SiteManager.RuinRecord ruin, ServerPlayer player) {
        BlockPos placed = site.mainframe;
        if (level.getBlockState(placed).is(ModBlocks.MAINFRAME)) {
            level.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
            Cost.give(player, new ItemStack(ModBlocks.MAINFRAME));
        }
        replaceRuined(level, ruin.mainframe);
        site.mainframe = ruin.mainframe;
        site.anchor = ruin.mainframe;
        adoptRooms(site, ruin);
        Msg.send(player, "You've moved into the old facility. Its rooms are damaged: repair them with Repair Kits or Engineers.", ChatFormatting.AQUA);
    }

    private static void adoptRooms(Site site, SiteManager.RuinRecord ruin) {
        ruin.claimed = true;
        for (Room r : ruin.rooms) {
            r.number = site.nextRoomNumber++;
            r.built = true;
            r.integrity = 40;
            dev.projectredacted.room.RoomPlanner.name(site, r, r.typeDef());
            site.rooms.add(r);
        }
        site.log("Moved into an abandoned facility.");
    }

    private SiteFounding() {}
}
