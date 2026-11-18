package dev.chysalis.propagate.net;

import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PropagateNet {
    private PropagateNet() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
    }

    /** Sends the active settings to one player (if their client has Propagate). */
    public static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ConfigPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ConfigPayload(Rules.settings().toJson()));
        }
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }
}
