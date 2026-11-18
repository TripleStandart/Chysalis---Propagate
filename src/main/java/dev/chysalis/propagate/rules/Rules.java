package dev.chysalis.propagate.rules;

import dev.chysalis.propagate.Propagate;
import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.config.Settings;
import dev.chysalis.propagate.geom.Side;
import dev.chysalis.propagate.place.Guard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides, per block state, whether it is a support and which category of thing it is.
 * Results are cached on the block state itself and dropped when the config or the tags change.
 */
public final class Rules {
    private static volatile int generation = 1;
    private static volatile Compiled compiled = Compiled.of(Settings.defaults());

    private Rules() {}

    public static Settings settings() {
        return compiled.settings;
    }

    public static int generation() {
        return generation;
    }

    /** New config: recompile the matchers and forget every cached decision. */
    public static void apply(Settings s) {
        compiled = Compiled.of(s);
        invalidate();
    }

    /** Tags were (re)loaded or something else changed what the matchers see. */
    public static void invalidate() {
        generation++;
        dev.chysalis.propagate.place.Placement.clearCaches();
    }

    // ------------------------------------------------------------------ lookups (hot)

    public static Rule rule(BlockState st) {
        int g = generation;
        StateCache c = (StateCache) st;
        Rule r = c.propagate$rule(g);
        if (r != null) return r;
        Guard.enter();
        try {
            r = resolve(st);
        } catch (Throwable t) {
            Propagate.LOG.warn("Propagate: could not categorize {}, leaving it alone", st, t);
            r = Rule.none("error while categorizing: " + t);
        } finally {
            Guard.exit();
        }
        c.propagate$setRule(r, g);
        return r;
    }

    public static boolean isSupport(BlockState st) {
        int g = generation;
        StateCache c = (StateCache) st;
        int v = c.propagate$support(g);
        if (v >= 0) return v == 1;
        boolean s = compiled.isSupport(st);
        c.propagate$setSupport(s, g);
        return s;
    }

    // ------------------------------------------------------------------ resolution

    private static Rule resolve(BlockState st) {
        Compiled cc = compiled;
        Settings s = cc.settings;
        if (!s.enabled) return Rule.none("Propagate is disabled in the config");
        Block b = st.getBlock();
        if (st.isAir()) return Rule.none("air");
        if (b instanceof LiquidBlock) return Rule.none("liquid");
        if (isSupport(st)) return Rule.none("it is a support itself");

        String[] match = cc.match(st);
        if (match == null) return Rule.none("no entry in \"blocks\" matches");
        String catName = match[0], why = match[1];
        if (Settings.NONE.equals(catName)) return Rule.none(why + " -> none");
        Category cat = s.categories.get(catName);
        if (cat == null) return Rule.none(why + " -> unknown category " + catName);

        if (!s.allowFullBlocks && isFullBlock(st)) return Rule.none(why + ", but it is a full block (allow_full_blocks is off)");

        Side anchor = Anchors.resolve(st, cat.anchor());
        if (anchor == null) return Rule.none(why + " -> " + cat.name() + ", but this state has no single surface to rest on");
        Rule.Link link = Anchors.link(st);
        return new Rule(cat, anchor, link, link == Rule.Link.PAIR ? Anchors.partner(st) : null, why + " -> " + cat.name());
    }

    private static boolean isFullBlock(BlockState st) {
        try {
            return Block.isShapeFullBlock(st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ compiled matchers

    private record Entry<T>(T key, String value, String raw) {}

    private static final class Compiled {
        final Settings settings;
        final Map<Identifier, String[]> ids = new HashMap<>();
        final List<Entry<TagKey<Block>>> tags = new ArrayList<>();
        final List<Entry<Class<?>>> classes = new ArrayList<>();
        final Map<String, String[]> mods = new HashMap<>();
        final Set<Identifier> supportIds = new HashSet<>();
        final List<TagKey<Block>> supportTags = new ArrayList<>();
        final Set<String> supportMods = new HashSet<>();

        private Compiled(Settings s) {
            this.settings = s;
        }

        static Compiled of(Settings s) {
            Compiled c = new Compiled(s);
            for (Map.Entry<String, String> e : s.blocks.entrySet()) {
                String k = e.getKey(), v = e.getValue();
                if (k.startsWith("#")) {
                    Identifier id = Identifier.tryParse(k.substring(1));
                    if (id == null) warn("bad tag " + k);
                    else c.tags.add(new Entry<>(TagKey.create(Registries.BLOCK, id), v, k));
                } else if (k.startsWith("class:")) {
                    String name = k.substring(6).trim();
                    try {
                        c.classes.add(new Entry<>(Class.forName(name, false, Rules.class.getClassLoader()), v, k));
                    } catch (Throwable t) {
                        Propagate.LOG.debug("Propagate: class {} not present, entry skipped", name);
                    }
                } else if (k.startsWith("mod:")) {
                    c.mods.putIfAbsent(k.substring(4).trim(), new String[]{v, k});
                } else {
                    Identifier id = Identifier.tryParse(k);
                    if (id == null) warn("bad block id " + k);
                    else c.ids.putIfAbsent(id, new String[]{v, k});
                }
            }
            for (String k : s.supports) {
                if (k.startsWith("#")) {
                    Identifier id = Identifier.tryParse(k.substring(1));
                    if (id != null) c.supportTags.add(TagKey.create(Registries.BLOCK, id));
                    else warn("bad support tag " + k);
                } else if (k.startsWith("mod:")) {
                    c.supportMods.add(k.substring(4).trim());
                } else {
                    Identifier id = Identifier.tryParse(k);
                    if (id != null) c.supportIds.add(id);
                    else warn("bad support id " + k);
                }
            }
            return c;
        }

        private static void warn(String msg) {
            Propagate.LOG.warn("Propagate config: {}", msg);
        }

        boolean isSupport(BlockState st) {
            if (st.isAir()) return false;
            Identifier id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            if (supportIds.contains(id) || supportMods.contains(id.getNamespace())) return true;
            for (TagKey<Block> t : supportTags) if (st.is(t)) return true;
            return false;
        }

        /** {category, reason} or null. Precedence: id, tag (file order), class (most specific), mod. */
        String[] match(BlockState st) {
            Block b = st.getBlock();
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            String[] hit = ids.get(id);
            if (hit != null) return new String[]{hit[0], "\"" + hit[1] + "\""};
            for (Entry<TagKey<Block>> t : tags) {
                if (st.is(t.key())) return new String[]{t.value(), "tag \"" + t.raw() + "\""};
            }
            Entry<Class<?>> best = null;
            int bestDepth = -1;
            for (Entry<Class<?>> e : classes) {
                if (e.key().isInstance(b)) {
                    int d = depth(e.key());
                    if (d > bestDepth) {
                        bestDepth = d;
                        best = e;
                    }
                }
            }
            if (best != null) return new String[]{best.value(), "\"" + best.raw() + "\""};
            hit = mods.get(id.getNamespace());
            if (hit != null) return new String[]{hit[0], "\"" + hit[1] + "\""};
            return null;
        }

        private static int depth(Class<?> c) {
            int d = 0;
            for (Class<?> k = c; k != null; k = k.getSuperclass()) d++;
            return d;
        }
    }
}
