package dev.chysalis.propagate.compat;

import dev.chysalis.propagate.Propagate;
import dev.chysalis.propagate.command.PropagateCommands;
import dev.chysalis.propagate.rules.Rule;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Manual override with the Chysalis Wrench (Chysalis >= 0.3.9): use it on a thing Chysalis can't turn
 * (a lamp, a sconce ...) to cycle that block's Propagate category (default, standing, resting, hanging,
 * mounted, attached, fitted, none); attack with it to go back. Same as {@code /propagate set}, saved to
 * the config and sent to everyone. Gamemasters (and the singleplayer world) only.
 */
public final class WrenchOverride {
    private WrenchOverride() {}

    public static void init() {
        if (!ChysalisCompat.AVAILABLE) return;
        try {
            Class.forName("dev.chysalis.api.WrenchHandler", false, WrenchOverride.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            Propagate.LOG.info("Propagate: Chysalis is older than 0.3.9, no wrench override (use /propagate set).");
            return;
        }
        Hook.register();
    }

    /** Only things that could be moved: not supports, not full cubes. */
    static boolean candidate(BlockState st) {
        if (st.isAir() || Rules.isSupport(st) || !st.getFluidState().isEmpty() && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        Rule r = Rules.rule(st);
        if (!r.isNone()) return true;
        try {
            return st.hasBlockEntity() || !Block.isShapeFullBlock(st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean handle(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState st, boolean back) {
        if (!candidate(st)) return false;
        var server = level.getServer();
        if (!server.isSingleplayer() && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            bar(player, Component.literal("Propagate: only gamemasters can change categories"));
            return true;
        }
        List<String> order = PropagateCommands.cycle();
        int i = order.indexOf(PropagateCommands.entryOf(st.getBlock()));
        String next = order.get(Math.floorMod((i < 0 ? 0 : i) + (back ? -1 : 1), order.size()));
        PropagateCommands.assign(server, st.getBlock(), next);
        Rule r = Rules.rule(st);
        String now = r.isNone() ? "not moved" : r.category().name() + ", " + r.anchor().name().toLowerCase(Locale.ROOT);
        bar(player, Component.literal("Propagate: " + next + " (" + now + ")"));
        // let neighbours and the client re-check the thing right away
        level.sendBlockUpdated(pos, st, st, Block.UPDATE_ALL);
        return true;
    }

    private static void bar(ServerPlayer player, Component msg) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(msg));
    }

    private static final class Hook {
        static void register() {
            dev.chysalis.api.ChysalisApi.registerWrenchHandler((player, level, pos, state, face, action) ->
                    handle(player, level, pos, state, action == dev.chysalis.api.WrenchHandler.Action.TURN_BACK));
        }
    }
}
