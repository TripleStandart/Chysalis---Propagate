package dev.chysalis.propagate.place;

import dev.chysalis.propagate.Propagate;
import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.geom.Box;
import dev.chysalis.propagate.geom.DepthMap;
import dev.chysalis.propagate.geom.Fit;
import dev.chysalis.propagate.geom.Footprint;
import dev.chysalis.propagate.geom.Side;
import dev.chysalis.propagate.rules.Anchors;
import dev.chysalis.propagate.rules.Rule;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a categorized thing sits: measures the support's outline (voxel by voxel, so chiseled blocks,
 * Shaper shapes, slabs and stairs all work) under the thing's footprint and moves the thing onto the
 * highest point, never into the support. Pure function of the world, so the server and every client
 * agree without storing anything.
 */
public final class Placement {
    private static final Map<VoxelShape, DepthMap[]> DEPTHS = new WeakHashMap<>();
    @SuppressWarnings("unchecked")
    private static final Map<BlockState, Footprint>[] FOOTPRINTS = new Map[]{
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>()};
    private static volatile boolean warned;

    private Placement() {}

    public static void clearCaches() {
        for (Map<BlockState, Footprint> m : FOOTPRINTS) m.clear();
    }

    // ------------------------------------------------------------------ offsets

    /** How far the thing at {@code pos} is moved, or null if it stays where vanilla puts it. */
    public static Vec3 offset(BlockGetter level, BlockPos pos, BlockState st) {
        return offset(level, level, pos, st);
    }

    /**
     * @param shapeLevel where supports with a block entity (chiseled blocks) read their shape; chunk meshing
     *                   passes the client level here because its section snapshot may not carry them
     */
    public static Vec3 offset(BlockGetter level, BlockGetter shapeLevel, BlockPos pos, BlockState st) {
        Rule r = Rules.rule(st);
        if (!r.moves()) return null;
        Guard.enter();
        try {
            double max = Rules.settings().maxOffset;
            Side anchor = r.anchor();
            double d;
            switch (r.link()) {
                case UPPER -> {
                    BlockPos lp = pos.below();
                    BlockState ls = level.getBlockState(lp);
                    Rule lr = Rules.rule(ls);
                    if (ls.getBlock() != st.getBlock() || lr.link() != Rule.Link.LOWER || !lr.moves()) return null;
                    anchor = lr.anchor();
                    d = own(level, shapeLevel, lp, ls, lr, max);
                }
                case PAIR -> {
                    d = own(level, shapeLevel, pos, st, r, max);
                    BlockPos pp = d > 0 ? partner(level, pos, st, r) : null;
                    if (pp != null) {
                        BlockState ps = level.getBlockState(pp);
                        Rule pr = Rules.rule(ps);
                        d = pr.moves() && pr.anchor() == anchor ? Math.min(d, own(level, shapeLevel, pp, ps, pr, max)) : 0.0;
                    }
                }
                default -> d = own(level, shapeLevel, pos, st, r, max);
            }
            if (d <= 0.0) return null;
            Direction dir = Anchors.direction(anchor);
            return new Vec3(dir.getStepX() * d, dir.getStepY() * d, dir.getStepZ() * d);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Propagate.LOG.error("Propagate: could not place {} at {}, leaving it where vanilla puts it", st, pos, t);
            }
            return null;
        } finally {
            Guard.exit();
        }
    }

    private static double own(BlockGetter level, BlockGetter shapeLevel, BlockPos pos, BlockState st, Rule r, double max) {
        Fit f = fit(level, shapeLevel, pos, st, r.category(), r.anchor());
        return f == null ? 0.0 : f.appliedOffset(max);
    }

    /** The other half of a bed / double chest, or null. */
    private static BlockPos partner(BlockGetter level, BlockPos pos, BlockState st, Rule r) {
        Direction[] dirs = r.partner();
        if (dirs == null) return null;
        for (Direction d : dirs) {
            BlockPos p = pos.relative(d);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() == st.getBlock() && Anchors.link(s) == Rule.Link.PAIR) {
                Direction[] back = Anchors.partner(s);
                if (back == null) continue;
                for (Direction b : back) if (b == d.getOpposite()) return p;
            }
        }
        return null;
    }

    /**
     * How the thing meets the support on its {@code anchor} side, or null if that block is not a support.
     */
    public static Fit fit(BlockGetter level, BlockGetter shapeLevel, BlockPos pos, BlockState st, Category cat, Side anchor) {
        Direction dir = Anchors.direction(anchor);
        BlockPos sp = pos.relative(dir);
        BlockState ss = level.getBlockState(sp);
        if (!Rules.isSupport(ss)) return null;
        VoxelShape shape = ss.getShape(ss.hasBlockEntity() ? shapeLevel : level, sp);
        return Fit.of(depth(shape, anchor), footprint(st, anchor.axis, cat), cat.tolerance());
    }

    // ------------------------------------------------------------------ survival

    /**
     * True if {@code support} holds {@code thing} on its {@code face} (the thing is at supportPos + face).
     * Asked by the support's isFaceSturdy while the thing checks whether it may be placed / stay.
     */
    public static boolean holds(BlockGetter level, BlockPos supportPos, BlockState support, Direction face, BlockState thing) {
        Rule r = Rules.rule(thing);
        if (r.isNone()) return false;
        Guard.enter();
        try {
            Side anchor = Anchors.side(face.getOpposite());
            VoxelShape shape = support.getShape(level, supportPos);
            Category cat = r.category();
            Fit f = Fit.of(depth(shape, anchor), footprint(thing, anchor.axis, cat), cat.tolerance());
            return f.supports(cat.minCoverage(), Rules.settings().maxOffset);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Propagate.LOG.error("Propagate: support check failed for {} on {}", thing, support, t);
            }
            return false;
        } finally {
            Guard.exit();
        }
    }

    // ------------------------------------------------------------------ caches

    public static DepthMap depth(VoxelShape shape, Side toward) {
        synchronized (DEPTHS) {
            DepthMap[] arr = DEPTHS.computeIfAbsent(shape, k -> new DepthMap[6]);
            DepthMap m = arr[toward.ordinal()];
            if (m == null) {
                m = DepthMap.of(boxes(shape), toward);
                arr[toward.ordinal()] = m;
            }
            return m;
        }
    }

    public static Footprint footprint(BlockState st, int axis, Category cat) {
        return FOOTPRINTS[axis].computeIfAbsent(st, s -> computeFootprint(s, axis, cat));
    }

    private static Footprint computeFootprint(BlockState st, int axis, Category cat) {
        switch (cat.footprint()) {
            case CENTER -> {
                return Footprint.centre();
            }
            case FULL -> {
                return Footprint.full();
            }
            default -> {
            }
        }
        List<BlockState> states = new ArrayList<>(List.of(st));
        for (String name : cat.unionProperties()) {
            Property<?> p = Anchors.property(st, name);
            if (p == null) continue;
            List<BlockState> next = new ArrayList<>();
            for (BlockState s : states) {
                for (Object v : p.getPossibleValues()) {
                    if (next.size() < 64) next.add(with(s, p, v));
                }
            }
            states = next;
        }
        Footprint f = null;
        Guard.enter();
        try {
            for (BlockState s : states) {
                Footprint g = Footprint.of(boxes(s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)), axis);
                f = f == null ? g : f.union(g);
            }
        } catch (Throwable t) {
            return Footprint.full(); // a shape that needs the world: assume it covers the whole face
        } finally {
            Guard.exit();
        }
        return f == null ? Footprint.centre() : f;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState s, Property p, Object v) {
        return s.setValue(p, (Comparable) v);
    }

    public static List<Box> boxes(VoxelShape shape) {
        List<AABB> list = shape.toAabbs();
        List<Box> out = new ArrayList<>(list.size());
        for (AABB a : list) out.add(new Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
        return out;
    }
}
