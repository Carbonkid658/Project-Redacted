package dev.projectredacted.site;

import dev.projectredacted.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** C.A.S.S.I.E. announcements: a chime, then the message in chat. Silent if C.A.S.S.I.E. is missing, disabled or hacked. */
public final class Cassie {
    public static boolean announce(Site site, String message) {
        if (!site.cassieOperational()) return false;
        broadcast(site, Component.literal("[C.A.S.S.I.E.] ").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.AQUA)), true);
        return true;
    }

    /** Alarms work without C.A.S.S.I.E.; they're the first sign of a raid when there is no early warning. */
    public static void alarm(Site site, String message) {
        broadcast(site, Component.literal("[ALARM] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.RED)), false);
        for (ServerPlayer p : playersInSite(site)) {
            p.level().playSound(null, p.blockPosition(), ModSounds.SITE_ALARM, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    /** Messages every online member (the Director and players), wherever they are. */
    public static void broadcast(Site site, Component message, boolean chime) {
        MinecraftServer server = SiteManager.server();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(site.director) || site.members.containsKey(p.getUUID())) {
                p.sendSystemMessage(message);
                if (chime) p.level().playSound(null, p.blockPosition(), ModSounds.CASSIE_ANNOUNCE, SoundSource.VOICE, 1f, 1f);
            }
        }
        site.log(message.getString());
    }

    public static java.util.List<ServerPlayer> playersInSite(Site site) {
        java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
        MinecraftServer server = SiteManager.server();
        if (server == null) return list;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == server.overworld() && site.contains(p.blockPosition())) list.add(p);
        }
        return list;
    }

    private Cassie() {}
}
