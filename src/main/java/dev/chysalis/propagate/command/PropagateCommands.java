package dev.chysalis.propagate.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.config.ConfigFile;
import dev.chysalis.propagate.config.Settings;
import dev.chysalis.propagate.geom.Fit;
import dev.chysalis.propagate.net.PropagateNet;
import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.rules.Rule;
import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <ul>
 *   <li>{@code /propagate inspect} - what Propagate does with the looked-at thing (or the thing on the looked-at support)</li>
 *   <li>{@code /propagate categories} - the categories and their settings</li>
 *   <li>{@code /propagate set <block> <category|none|default>} - put a block into a category (gamemaster)</li>
 *   <li>{@code /propagate reload} - re-read config/propagate.json and send it to everyone (gamemaster)</li>
 * </ul>
 */
public final class PropagateCommands {
    private PropagateCommands() {}

    private static boolean gm(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, b) -> {
        List<String> names = new ArrayList<>(Rules.settings().categories.keySet());
        names.add(Settings.NONE);
        names.add("default");
        return SharedSuggestionProvider.suggest(names, b);
    };

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("propagate")
                        .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource())))
                        .then(Commands.literal("categories").executes(ctx -> categories(ctx.getSource())))
                        .then(Commands.literal("reload").requires(PropagateCommands::gm)
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(Commands.literal("set").requires(PropagateCommands::gm)
                                .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests(CATEGORIES)
                                                .executes(ctx -> set(ctx.getSource(),
                                                        BlockStateArgument.getBlock(ctx, "block").getState().getBlock(),
                                                        StringArgumentType.getString(ctx, "category"))))))));
    }

    private static void say(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int reload(CommandSourceStack src) {
        List<String> problems = new ArrayList<>();
        Settings s = ConfigFile.load(problems);
        Rules.apply(s);
        PropagateNet.broadcast(src.getServer());
        say(src, "Propagate: reloaded (" + s.categories.size() + " categories, " + s.blocks.size() + " block entries).");
        for (String p : problems) src.sendFailure(Component.literal("  " + p));
        return 1;
    }

    private static int set(CommandSourceStack src, Block block, String category) {
        String cat = category.toLowerCase(Locale.ROOT);
        if (!cat.equals("default") && !cat.equals(Settings.NONE) && !Rules.settings().categories.containsKey(cat)) {
            src.sendFailure(Component.literal("Unknown category \"" + category + "\". Try /propagate categories."));
            return 0;
        }
        say(src, assign(src.getServer(), block, cat));
        return 1;
    }

    /** Puts a block into a category ("default" removes its entry), saves and syncs; returns the message. */
    public static String assign(net.minecraft.server.MinecraftServer server, Block block, String cat) {
        String key = BuiltInRegistries.BLOCK.getKey(block).toString();
        Settings s = Rules.settings().withBlock(key, cat.equals("default") ? null : cat);
        Rules.apply(s);
        boolean saved = ConfigFile.save(s);
        PropagateNet.broadcast(server);
        return "Propagate: " + key + " -> " + (cat.equals("default") ? "default (class / tag / mod rules, fallback)" : cat)
                + (saved ? "" : " (could not write the config file, change lasts until restart)");
    }

    /** The category a block's own entry gives it, or "default". */
    public static String entryOf(Block block) {
        String v = Rules.settings().blocks.get(BuiltInRegistries.BLOCK.getKey(block).toString());
        return v == null ? "default" : v;
    }

    /** default, the categories, none - the order the wrench cycles through. */
    public static List<String> cycle() {
        List<String> out = new ArrayList<>();
        out.add("default");
        out.addAll(Rules.settings().categories.keySet());
        out.add(Settings.NONE);
        return out;
    }

    private static int categories(CommandSourceStack src) {
        for (Category c : Rules.settings().categories.values()) {
            say(src, String.format(Locale.ROOT, "%s: anchor %s, %s, coverage >= %.0f%%, tolerance %.1f voxels, footprint %s%s%s",
                    c.name(), c.anchor().key(), c.needsSupport() ? "needs support" : "never needs support",
                    c.minCoverage() * 100, c.tolerance(), c.footprint().key(),
                    c.unionProperties().isEmpty() ? "" : ", shared states " + c.unionProperties(),
                    c.move() ? "" : ", not moved"));
        }
        return 1;
    }

    private static int inspect(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        HitResult hit = player.pick(6.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) {
            src.sendFailure(Component.literal("Look at a block first."));
            return 0;
        }
        Level level = player.level();
        BlockPos pos = bh.getBlockPos();
        BlockState st = level.getBlockState(pos);
        if (Rules.isSupport(st)) {
            say(src, "Support: " + BuiltInRegistries.BLOCK.getKey(st.getBlock()) + " at " + pos.toShortString());
            BlockPos above = pos.above();
            BlockState as = level.getBlockState(above);
            if (as.isAir()) return 1;
            pos = above;
            st = as;
        }
        Rule r = Rules.rule(st);
        say(src, BuiltInRegistries.BLOCK.getKey(st.getBlock()) + " at " + pos.toShortString() + ": " + r.why());
        if (r.isNone()) return 1;
        Category c = r.category();
        say(src, "  anchor " + r.anchor().name().toLowerCase(Locale.ROOT) + (r.link() == Rule.Link.NONE ? "" : ", linked parts: " + r.link().name().toLowerCase(Locale.ROOT)));
        Fit f = Placement.fit(level, level, pos, st, c, r.anchor());
        if (f == null) {
            say(src, "  no support on that side (vanilla rules apply)");
        } else {
            double max = Rules.settings().maxOffset;
            say(src, String.format(Locale.ROOT, "  surface %s, touches %.0f%% of its footprint (needs %.0f%%), sinks %.2f blocks%s",
                    f.contact() ? "found" : "missing", f.coverage() * 100, c.minCoverage() * 100, f.offset(),
                    f.offset() > max + 1e-9 ? " (more than max_offset " + max + ", not moved)" : ""));
            say(src, "  " + (c.needsSupport() ? (f.supports(c.minCoverage(), max) ? "held by the surface" : "NOT held by the surface") : "never needs support"));
        }
        Vec3 o = Placement.offset(level, pos, st);
        say(src, "  moved by " + (o == null ? "nothing" : String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", o.x, o.y, o.z)));
        return 1;
    }
}
