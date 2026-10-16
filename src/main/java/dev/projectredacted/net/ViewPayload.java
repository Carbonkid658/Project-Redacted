package dev.projectredacted.net;

import dev.projectredacted.ProjectRedacted;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: everything a screen shows, as JSON built by a {@link dev.projectredacted.gui.GuiScreen}.
 * The client draws it with the shared widget kit. {@code serial} identifies one opening of a screen, so clicks
 * on a screen that has since been replaced are ignored.
 */
public record ViewPayload(int serial, String screen, String json) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 1 << 18;
    public static final Type<ViewPayload> TYPE = new Type<>(ProjectRedacted.id("view"));
    public static final StreamCodec<FriendlyByteBuf, ViewPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.serial);
                buf.writeUtf(p.screen);
                buf.writeUtf(p.json, MAX_LENGTH);
            },
            buf -> new ViewPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(MAX_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
