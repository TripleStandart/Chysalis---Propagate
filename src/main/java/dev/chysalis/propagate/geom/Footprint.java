package dev.chysalis.propagate.geom;

import java.util.List;

/**
 * The 1/16 cells a thing covers when looked at along one axis (its shadow on the support's face).
 * Same cell layout as {@link DepthMap}: index = u * 16 + v.
 */
public final class Footprint {
    private final long[] bits = new long[4];
    private int count;

    private Footprint() {}

    /** Shadow of the boxes along {@code axis}. Empty shapes (or shapes too thin to see) get the centre 4x4 cells. */
    public static Footprint of(List<Box> boxes, int axis) {
        Footprint f = new Footprint();
        int ua = Side.uAxis(axis), va = Side.vAxis(axis);
        for (Box b : boxes) {
            if (b.max(ua) - b.min(ua) <= 1e-9 || b.max(va) - b.min(va) <= 1e-9) continue;
            int u0 = DepthMap.cellFrom(b.min(ua)), u1 = DepthMap.cellTo(b.max(ua));
            int v0 = DepthMap.cellFrom(b.min(va)), v1 = DepthMap.cellTo(b.max(va));
            for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) f.set(u * 16 + v);
        }
        if (f.count == 0) f.fillCentre();
        return f;
    }

    /** The centre 4x4 cells (vanilla's "center support" area for torches and the like). */
    public static Footprint centre() {
        Footprint f = new Footprint();
        f.fillCentre();
        return f;
    }

    /** Every cell of the face. */
    public static Footprint full() {
        Footprint f = new Footprint();
        for (int i = 0; i < 256; i++) f.set(i);
        return f;
    }

    private void fillCentre() {
        for (int u = 6; u < 10; u++) for (int v = 6; v < 10; v++) set(u * 16 + v);
    }

    private void set(int i) {
        long m = 1L << (i & 63);
        if ((bits[i >> 6] & m) == 0) {
            bits[i >> 6] |= m;
            count++;
        }
    }

    public boolean has(int i) {
        return (bits[i >> 6] & (1L << (i & 63))) != 0;
    }

    public boolean has(int u, int v) {
        return has(u * 16 + v);
    }

    public int count() {
        return count;
    }

    /** A new footprint covering both. */
    public Footprint union(Footprint o) {
        Footprint f = new Footprint();
        for (int k = 0; k < 4; k++) f.bits[k] = bits[k] | o.bits[k];
        for (long w : f.bits) f.count += Long.bitCount(w);
        return f;
    }
}
