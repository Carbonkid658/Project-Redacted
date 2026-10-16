package dev.projectredacted.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.projectredacted.raid.RaidManager;
import dev.projectredacted.room.Room;
import dev.projectredacted.site.Member;
import dev.projectredacted.site.Site;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** /site: info, invite, remove, clearance, transfer and leave. */
public final class SiteCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("site")
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> remove(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("clearance").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> clearance(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("transfer").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> transfer(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource().getPlayerOrException()))));
    }

    private static Site directed(ServerPlayer player) {
        Site site = SiteManager.directedBy(player.getUUID());
        if (site == null) Msg.send(player, "You don't direct a site.", ChatFormatting.RED);
        return site;
    }

    private static int info(ServerPlayer player) {
        Site site = SiteManager.memberSite(player);
        if (site == null) {
            Msg.send(player, "You aren't part of a site. Craft a Mainframe and place it to found one.", ChatFormatting.GRAY);
            return 0;
        }
        Msg.send(player, "Site of Director " + site.directorName + " (Level " + site.level + ")", ChatFormatting.GOLD);
        Msg.send(player, "Claim: " + site.claimChunks() + "x" + site.claimChunks() + " chunks around " + site.center().toShortString(), ChatFormatting.GRAY);
        Msg.send(player, "Mainframe " + (site.mainframeOnline() ? "online" : "OFFLINE") + ", power " + site.powerDemand + "/" + site.powerSupply
                + ", water " + site.waterDemand + "/" + site.waterSupply, ChatFormatting.GRAY);
        Msg.send(player, "Staff " + site.housedStaff() + "/" + site.beds + " beds, SCPs " + site.scpCount + String.format(", raid risk %.1f%%/min",
                RaidManager.chancePerMinute(site) * 100), ChatFormatting.GRAY);
        for (Room r : site.rooms) Msg.send(player, "  " + r.displayName() + (r.poweredNow ? " ⚡" : "") + (r.built ? "" : " (building)"), ChatFormatting.DARK_GRAY);
        return 1;
    }

    private static int invite(ServerPlayer player, ServerPlayer target) {
        Site site = directed(player);
        if (site == null) return 0;
        if (target == player) return 0;
        site.members.put(target.getUUID(), new Member(target.getName().getString()));
        Msg.send(player, target.getName().getString() + " joined your site with Level 1 clearance.", ChatFormatting.GREEN);
        Msg.send(target, "You're now part of " + site.directorName + "'s site.", ChatFormatting.GREEN);
        return 1;
    }

    private static int remove(ServerPlayer player, ServerPlayer target) {
        Site site = directed(player);
        if (site == null) return 0;
        if (site.members.remove(target.getUUID()) != null) Msg.send(player, target.getName().getString() + " removed.", ChatFormatting.YELLOW);
        return 1;
    }

    private static int clearance(ServerPlayer player, ServerPlayer target, int level) {
        Site site = directed(player);
        if (site == null) return 0;
        Member m = site.members.get(target.getUUID());
        if (m == null) {
            Msg.send(player, target.getName().getString() + " isn't part of your site.", ChatFormatting.RED);
            return 0;
        }
        m.clearance = level;
        Msg.send(player, m.name + " now has Level " + level + " clearance.", ChatFormatting.GREEN);
        return 1;
    }

    private static int transfer(ServerPlayer player, ServerPlayer target) throws CommandSyntaxException {
        Site site = directed(player);
        if (site == null) return 0;
        if (SiteManager.directedBy(target.getUUID()) != null) {
            Msg.send(player, target.getName().getString() + " already directs a site.", ChatFormatting.RED);
            return 0;
        }
        site.members.remove(target.getUUID());
        Member old = new Member(player.getName().getString());
        old.clearance = 4;
        old.terminal = true;
        old.assign = true;
        site.members.put(player.getUUID(), old);
        site.director = target.getUUID();
        site.directorName = target.getName().getString();
        site.log("Directorship transferred to " + site.directorName + ".");
        Msg.send(player, "You've handed the site to " + site.directorName + ".", ChatFormatting.GOLD);
        Msg.send(target, "You are now the Site Director.", ChatFormatting.GOLD);
        return 1;
    }

    private static int leave(ServerPlayer player) {
        for (Site s : SiteManager.sites()) {
            if (s.members.remove(player.getUUID()) != null) {
                Msg.send(player, "You left " + s.directorName + "'s site.", ChatFormatting.YELLOW);
                return 1;
            }
        }
        Msg.send(player, "Directors can't leave; transfer the site first.", ChatFormatting.GRAY);
        return 0;
    }

    private SiteCommand() {}
}
