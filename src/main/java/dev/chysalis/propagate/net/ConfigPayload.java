package dev.chysalis.propagate.net;

import dev.chysalis.propagate.Propagate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the server's Propagate settings (JSON), so both sides move things the same way. */
public record ConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<ConfigPayload> TYPE = new Type<>(Propagate.id("config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 << 20), ConfigPayload::json, ConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
