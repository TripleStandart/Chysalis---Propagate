package dev.chysalis.propagate.client;

import dev.chysalis.propagate.Propagate;
import dev.chysalis.propagate.config.ConfigFile;
import dev.chysalis.propagate.config.Settings;
import dev.chysalis.propagate.net.ConfigPayload;
import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class PropagateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OffsetModel.register();
        // While connected, the server's settings decide (both sides must move things the same way).
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            List<String> problems = new ArrayList<>();
            Settings s = Settings.parse(payload.json(), problems);
            for (String p : problems) Propagate.LOG.warn("Propagate (server config): {}", p);
            boolean changed = !s.toJson().equals(Rules.settings().toJson());
            Rules.apply(s);
            if (changed) remesh();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Rules.apply(ConfigFile.load()));
    }

    /** Rebuilds the chunk meshes so models pick up new offsets. */
    static void remesh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelExtractor != null && mc.level != null) mc.levelExtractor.allChanged();
    }
}
