package dev.chysalis.propagate.geom;

import java.util.Arrays;
import java.util.List;

/**
 * How far the surface of a supporting block lies behind one of its faces, per 1/16 cell of that face.
 * <p>
 * Built for the side the thing <em>sees</em> the support from: {@code toward} is the direction from the
 * thing to the support (DOWN for a thing standing on the block below, UP for a thing hanging from the
 * block above, NORTH for a thing fixed to a block north of it). Depth 0 = material right at the face,
 * 16 = no material in that column at all.
 * <p>
 * A cell counts as covered by a box as soon as they overlap, so a thing resting on the result can never
 * cut into the support, whatever the support's shape (chiseled voxels, smooth Shaper shapes, slabs ...).
 */
public final class DepthMap {
    public static final float EMPTY = 16f;
    private static final double EPS = 1e-6;

    public final Side toward;
    private final float[] depth = new float[256];

    private DepthMap(Side toward) {
        this.toward = toward;
        Arrays.fill(depth, EMPTY);
    }

    public static DepthMap of(List<Box> supportBoxes, Side toward) {
        DepthMap m = new DepthMap(toward);
        int a = toward.axis, ua = Side.uAxis(a), va = Side.vAxis(a);
        for (Box b : supportBoxes) {
            double d = toward.sign < 0 ? (1.0 - b.max(a)) * 16.0 : b.min(a) * 16.0;
            if (d >= EMPTY - EPS) continue; // entirely beyond the far side
            float df = (float) Math.max(0.0, d);
            int u0 = cellFrom(b.min(ua)), u1 = cellTo(b.max(ua));
            int v0 = cellFrom(b.min(va)), v1 = cellTo(b.max(va));
            for (int u = u0; u <= u1; u++) {
                for (int v = v0; v <= v1; v++) {
                    int i = u * 16 + v;
                    if (df < m.depth[i]) m.depth[i] = df;
                }
            }
        }
        return m;
    }

    /** First cell a box starting at {@code min} overlaps. */
    static int cellFrom(double min) {
        return Math.max(0, (int) Math.floor(min * 16.0 + EPS));
    }

    /** Last cell a box ending at {@code max} overlaps. */
    static int cellTo(double max) {
        return Math.min(15, (int) Math.ceil(max * 16.0 - EPS) - 1);
    }

    public float depth(int u, int v) {
        return depth[u * 16 + v];
    }

    float depthAt(int index) {
        return depth[index];
    }

    /** True if the face has no material behind it anywhere. */
    public boolean isEmpty() {
        for (float d : depth) if (d < EMPTY) return false;
        return true;
    }
}
