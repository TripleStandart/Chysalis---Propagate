package dev.chysalis.propagate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything in {@code config/propagate.json}, parsed. Immutable; a reload builds a new one.
 * Plain Java (only Gson) so it can be unit-tested without Minecraft.
 */
public final class Settings {
    public static final String NONE = "none";

    public final boolean enabled;
    /** Blocks that count as partly chiseled surfaces: ids, #tags, mod:namespace. */
    public final List<String> supports;
    /** Deepest a thing may sink into a support, in blocks. */
    public final double maxOffset;
    /** Allow full cubes to be categorized (off: plain blocks are never moved). */
    public final boolean allowFullBlocks;
    /** Fix the crosshair so lowered parts below the thing's own cell can be clicked. */
    public final boolean fixTargeting;
    /** Move torch flames, candle smoke ... with the thing. */
    public final boolean moveParticles;
    /** Lay sleeping players on the lowered bed. */
    public final boolean moveSleepers;
    public final Map<String, Category> categories;
    /** Key (id, #tag, class:..., mod:...) to category name or "none", in file order. */
    public final Map<String, String> blocks;

    public Settings(boolean enabled, List<String> supports, double maxOffset, boolean allowFullBlocks,
                    boolean fixTargeting, boolean moveParticles, boolean moveSleepers,
                    Map<String, Category> categories, Map<String, String> blocks) {
        this.enabled = enabled;
        this.supports = List.copyOf(supports);
        this.maxOffset = Math.max(0.0, Math.min(1.0, maxOffset));
        this.allowFullBlocks = allowFullBlocks;
        this.fixTargeting = fixTargeting;
        this.moveParticles = moveParticles;
        this.moveSleepers = moveSleepers;
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }

    // ------------------------------------------------------------------ defaults

    private static final String B = "class:net.minecraft.world.level.block.";

    public static Map<String, Category> defaultCategories() {
        Map<String, Category> m = new LinkedHashMap<>();
        put(m, new Category("standing", Category.Anchor.DOWN, true, 0.5, 1.0,
                List.of("open", "powered", "north", "east", "south", "west"), true, Category.FootprintMode.SHAPE));
        put(m, new Category("resting", Category.Anchor.AUTO, false, 0.0, 1.0,
                List.of("open", "powered"), true, Category.FootprintMode.SHAPE));
        put(m, new Category("hanging", Category.Anchor.UP, true, 0.5, 1.0,
                List.of("powered"), true, Category.FootprintMode.SHAPE));
        put(m, new Category("mounted", Category.Anchor.SIDE, true, 0.5, 1.0,
                List.of("powered", "attached"), true, Category.FootprintMode.SHAPE));
        put(m, new Category("attached", Category.Anchor.AUTO, true, 0.5, 1.0,
                List.of("powered"), true, Category.FootprintMode.SHAPE));
        return m;
    }

    private static void put(Map<String, Category> m, Category c) {
        m.put(c.name(), c);
    }

    public static Map<String, String> defaultBlocks() {
        Map<String, String> m = new LinkedHashMap<>();
        // Data pack hooks: add blocks to these tags instead of editing this file.
        m.put("#propagate:never", NONE);
        for (String c : List.of("standing", "resting", "hanging", "mounted", "attached")) m.put("#propagate:" + c, c);
        // Chysalis' own blocks are surfaces, never things.
        m.put("mod:chysalis", NONE);

        // standing: on the block below, needs a good enough surface
        for (String c : List.of("DoorBlock", "BaseRailBlock", "BasePressurePlateBlock", "RedStoneWireBlock",
                "RedstoneWireBlock", "DiodeBlock", "BaseTorchBlock", "CandleBlock", "StandingSignBlock",
                "BannerBlock", "SeaPickleBlock")) m.put(B + c, "standing");
        // resting: never needs support, just sits on the surface
        for (String c : List.of("BedBlock", "AbstractChestBlock", "FlowerPotBlock", "SkullBlock", "DecoratedPotBlock",
                "CakeBlock", "CandleCakeBlock", "LecternBlock", "EnchantingTableBlock", "BrewingStandBlock",
                "CampfireBlock", "CarpetBlock", "MossyCarpetBlock", "TrapDoorBlock", "ChainBlock", "RodBlock",
                "ConduitBlock", "AnvilBlock", "HeavyCoreBlock", "GrindstoneBlock", "CopperGolemStatueBlock",
                "TripWireBlock")) m.put(B + c, "resting");
        // hanging: from the block above
        for (String c : List.of("CeilingHangingSignBlock", "SporeBlossomBlock", "HangingRootsBlock"))
            m.put(B + c, "hanging");
        // mounted: on the block behind (from "facing")
        for (String c : List.of("WallTorchBlock", "RedstoneWallTorchBlock", "LadderBlock", "WallSignBlock",
                "WallBannerBlock", "TripWireHookBlock", "WallSkullBlock")) m.put(B + c, "mounted");
        // attached: floor, ceiling or wall, read from the block state
        for (String c : List.of("LanternBlock", "FaceAttachedHorizontalDirectionalBlock", "BellBlock",
                "AmethystClusterBlock", "PointedDripstoneBlock")) m.put(B + c, "attached");
        return m;
    }

    public static Settings defaults() {
        return new Settings(true, List.of("chysalis:chiseled", "chysalis:shaped", "#propagate:supports"),
                15.0 / 16.0, false, true, true, true, defaultCategories(), defaultBlocks());
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses the config. Missing keys fall back to the defaults; categories are merged with the built-in
     * ones (so a config that only changes "standing" keeps the others). Comments are allowed.
     *
     * @param problems receives one line per thing that was ignored
     */
    public static Settings parse(String json, List<String> problems) {
        Settings d = defaults();
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new IllegalArgumentException("not a JSON object");
            o = e.getAsJsonObject();
        } catch (RuntimeException ex) {
            problems.add("could not read the file (" + ex.getMessage() + "), using the defaults");
            return d;
        }
        boolean enabled = bool(o, "enabled", d.enabled);
        List<String> supports = o.has("supports") ? strings(o.get("supports"), problems, "supports") : d.supports;
        double maxOffset = num(o, "max_offset", d.maxOffset);
        boolean full = bool(o, "allow_full_blocks", d.allowFullBlocks);
        boolean targeting = bool(o, "fix_targeting", d.fixTargeting);
        boolean particles = bool(o, "move_particles", d.moveParticles);
        boolean sleepers = bool(o, "move_sleepers", d.moveSleepers);

        Map<String, Category> cats = new LinkedHashMap<>(d.categories);
        if (o.has("categories") && o.get("categories").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("categories").entrySet()) {
                String name = en.getKey().trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty() || name.equals(NONE) || name.equals("default")) {
                    problems.add("category name \"" + en.getKey() + "\" is reserved");
                    continue;
                }
                if (!en.getValue().isJsonObject()) {
                    problems.add("category " + name + " is not an object");
                    continue;
                }
                Category base = cats.getOrDefault(name, new Category(name, Category.Anchor.AUTO, true, 0.5, 1.0,
                        List.of(), true, Category.FootprintMode.SHAPE));
                cats.put(name, category(name, en.getValue().getAsJsonObject(), base, problems));
            }
        }

        Map<String, String> blocks;
        if (o.has("blocks") && o.get("blocks").isJsonObject()) {
            blocks = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("blocks").entrySet()) {
                String key = en.getKey().trim();
                String val = en.getValue().isJsonPrimitive() ? en.getValue().getAsString().trim().toLowerCase(Locale.ROOT) : "";
                if (!val.equals(NONE) && !cats.containsKey(val)) {
                    problems.add("blocks: \"" + key + "\" uses unknown category \"" + val + "\"");
                    continue;
                }
                blocks.put(key, val);
            }
        } else {
            blocks = d.blocks;
        }
        return new Settings(enabled, supports, maxOffset, full, targeting, particles, sleepers, cats, blocks);
    }

    private static Category category(String name, JsonObject c, Category base, List<String> problems) {
        Category.Anchor anchor = c.has("anchor") ? Category.Anchor.parse(c.get("anchor").getAsString()) : base.anchor();
        boolean needs = bool(c, "needs_support", base.needsSupport());
        double cov = Math.max(0.0, Math.min(1.0, num(c, "min_coverage", base.minCoverage())));
        double tol = Math.max(0.0, Math.min(16.0, num(c, "tolerance", base.tolerance())));
        List<String> union = c.has("union_properties")
                ? strings(c.get("union_properties"), problems, "categories." + name + ".union_properties")
                : base.unionProperties();
        boolean move = bool(c, "move", base.move());
        Category.FootprintMode fp = c.has("footprint") ? Category.FootprintMode.parse(c.get("footprint").getAsString()) : base.footprint();
        return new Category(name, anchor, needs, cov, tol, union, move, fp);
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double num(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static List<String> strings(JsonElement e, List<String> problems, String where) {
        List<String> out = new ArrayList<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString().trim());
                else problems.add(where + ": ignored a non-string entry");
            }
        } else if (e.isJsonPrimitive()) {
            out.add(e.getAsString().trim());
        } else {
            problems.add(where + ": expected a list of strings");
        }
        return out;
    }

    // ------------------------------------------------------------------ writing

    /** A copy with one block entry changed ({@code value} null removes the entry). */
    public Settings withBlock(String key, String value) {
        Map<String, String> b = new LinkedHashMap<>();
        if (value != null) b.put(key, value); // explicit entries go first so they are easy to find
        for (Map.Entry<String, String> e : blocks.entrySet()) if (!e.getKey().equals(key)) b.put(e.getKey(), e.getValue());
        return new Settings(enabled, supports, maxOffset, allowFullBlocks, fixTargeting, moveParticles, moveSleepers, categories, b);
    }

    /** Compact JSON for the network (no comments). */
    public String toJson() {
        return new Gson().toJson(toObject());
    }

    private JsonObject toObject() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        JsonArray s = new JsonArray();
        supports.forEach(s::add);
        o.add("supports", s);
        o.addProperty("max_offset", maxOffset);
        o.addProperty("allow_full_blocks", allowFullBlocks);
        o.addProperty("fix_targeting", fixTargeting);
        o.addProperty("move_particles", moveParticles);
        o.addProperty("move_sleepers", moveSleepers);
        JsonObject cs = new JsonObject();
        for (Category c : categories.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("anchor", c.anchor().key());
            j.addProperty("needs_support", c.needsSupport());
            j.addProperty("min_coverage", c.minCoverage());
            j.addProperty("tolerance", c.tolerance());
            JsonArray u = new JsonArray();
            c.unionProperties().forEach(u::add);
            j.add("union_properties", u);
            j.addProperty("move", c.move());
            j.addProperty("footprint", c.footprint().key());
            cs.add(c.name(), j);
        }
        o.add("categories", cs);
        JsonObject b = new JsonObject();
        blocks.forEach(b::addProperty);
        o.add("blocks", b);
        return o;
    }

    /** The file as written to disk: the values plus an explanation on top. */
    public String toFileText() {
        String body = new GsonBuilder().setPrettyPrinting().create().toJson(toObject());
        return HEADER + body + "\n";
    }

    private static final String HEADER = """
            // Propagate - things on partly chiseled blocks (addon for Chysalis).
            // Reload in game with /propagate reload. On a server, the server's file is sent to every player.
            //
            // supports      blocks that count as partly chiseled surfaces: "namespace:id", "#namespace:tag"
            //               or "mod:namespace". Any block shape works (the outline is measured voxel by voxel).
            // max_offset    how deep (in blocks) a thing may sink into a support; 0.9375 = 15 voxels.
            // allow_full_blocks  false = plain full blocks are never moved, whatever "blocks" says.
            // fix_targeting / move_particles / move_sleepers  crosshair, flames and sleeping players follow
            //               the moved thing.
            //
            // categories    how a kind of thing finds its surface:
            //   anchor            "down" = stands on the block below, "up" = hangs from the block above,
            //                     "side" = fixed to the block behind it (from its "facing"),
            //                     "auto" = read from the block state (hanging, face, attachment, half,
            //                     6-way facing, vertical_direction), otherwise "down".
            //   needs_support     true = it may only be placed / stay where the surface holds it;
            //                     false = it stays wherever it is put and is just moved onto the surface.
            //   min_coverage      share (0..1) of its footprint that must touch the surface.
            //   tolerance         surface parts up to this many voxels lower than the highest point still touch.
            //   union_properties  states that differ only in these properties share one footprint
            //                     (so a door does not jump when it opens).
            //   move              false = only relax the support rules, never move it.
            //   footprint         "shape" (its own outline), "center" (centre 4x4) or "full" (whole face).
            //
            // blocks        which things belong to which category ("none" = never touched). Keys:
            //   "minecraft:oak_door"   one block            (wins over everything below)
            //   "#minecraft:doors"     a block tag          (first matching tag in this list wins)
            //   "class:<java class>"   that class and every subclass, so modded doors, beds, torches ...
            //                          are found automatically (the most specific class wins)
            //   "mod:<namespace>"      every block of a mod (weakest)
            // In game: /propagate set <block> <category|none|default>, /propagate inspect, /propagate categories.
            """;
}
