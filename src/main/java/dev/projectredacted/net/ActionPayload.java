package dev.projectredacted.net;

import dev.projectredacted.ProjectRedacted;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a button press on the open screen. The server re-checks everything, applies it and sends
 * back a fresh {@link ViewPayload}. Serial 0 is for actions with no screen open (the room preview keybinds).
 */
public record ActionPayload(int serial, String action, String json) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 4096;
    public static final Type<ActionPayload> TYPE = new Type<>(ProjectRedacted.id("action"));
    public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.serial);
                buf.writeUtf(p.action, 64);
                buf.writeUtf(p.json, MAX_LENGTH);
            },
            buf -> new ActionPayload(buf.readVarInt(), buf.readUtf(64), buf.readUtf(MAX_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
