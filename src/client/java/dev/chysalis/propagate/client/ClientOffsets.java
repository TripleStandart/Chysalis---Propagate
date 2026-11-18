package dev.chysalis.propagate.client;

import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Offsets for rendering: chunk models, block entities and particles. */
public final class ClientOffsets {
    private ClientOffsets() {}

    /** For the chunk mesher: block states from the section snapshot, chiseled shapes from the client level. */
    public static Vec3 forModel(BlockGetter view, BlockPos pos, BlockState state) {
        if (!Rules.rule(state).moves()) return null;
        ClientLevel level = Minecraft.getInstance().level;
        return Placement.offset(view, level != null ? level : view, pos, state);
    }

    /** For things drawn every frame (block entities): the client level. */
    public static Vec3 forPos(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || pos == null) return null;
        BlockState st = level.getBlockState(pos);
        if (!Rules.rule(st).moves()) return null;
        return Placement.offset(level, pos, st);
    }
}
