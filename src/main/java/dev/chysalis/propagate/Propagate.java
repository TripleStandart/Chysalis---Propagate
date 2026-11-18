package dev.chysalis.propagate;

import dev.chysalis.propagate.command.PropagateCommands;
import dev.chysalis.propagate.config.ConfigFile;
import dev.chysalis.propagate.net.PropagateNet;
import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Propagate (Chysalis addon): doors, beds, torches, lanterns, signs, rails ... on partly chiseled blocks.
 * Things are categorized by config (ids, tags, classes, mods), their footprint is measured against the
 * support's real outline, and they are moved onto the highest point under them.
 */
public final class Propagate implements ModInitializer {
    public static final String MOD_ID = "propagate";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Rules.apply(ConfigFile.load());
        // Tag-based entries ("#propagate:standing", supports tag ...) must be re-read when tags change.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> Rules.invalidate());
        PropagateNet.init();
        PropagateCommands.init();
        LOG.info("Propagate loaded - {} categories, {} block entries", Rules.settings().categories.size(),
                Rules.settings().blocks.size());
    }
}
