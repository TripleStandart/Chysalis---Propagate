package dev.chysalis.propagate.compat;

import dev.chysalis.propagate.Propagate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The only place that calls Chysalis (its public API, {@code dev.chysalis.api}, Chysalis >= 0.3.0).
 * Without Chysalis, or with an older one, {@link #AVAILABLE} is false and nothing here loads Chysalis classes.
 */
public final class ChysalisCompat {
    public static final boolean AVAILABLE = detect();

    private ChysalisCompat() {}

    private static boolean detect() {
        if (!FabricLoader.getInstance().isModLoaded("chysalis")) return false;
        try {
            Class.forName("dev.chysalis.api.ChysalisApi", false, ChysalisCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            Propagate.LOG.info("Propagate: Chysalis is older than 0.3.0 (no public API); carved blocks count as their own block for plants.");
            return false;
        }
    }

    /** Main material of a chiseled / shaped block, or null. */
    public static BlockState mainMaterial(BlockGetter level, BlockPos pos) {
        try {
            return Api.mainMaterial(level, pos);
        } catch (LinkageError e) {
            return null;
        }
    }

    /** Separate holder so the API class is resolved only when called. */
    private static final class Api {
        static BlockState mainMaterial(BlockGetter level, BlockPos pos) {
            return dev.chysalis.api.ChysalisApi.mainMaterial(level, pos);
        }
    }
}
