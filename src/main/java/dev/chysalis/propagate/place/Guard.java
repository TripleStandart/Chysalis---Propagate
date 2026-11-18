package dev.chysalis.propagate.place;

/**
 * Re-entrancy guard: while Propagate measures shapes (the thing's own outline, the support's outline),
 * its own shape hooks must return the vanilla answer. Per thread, because chunk meshing runs off-thread.
 */
public final class Guard {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private Guard() {}

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        int[] d = DEPTH.get();
        if (d[0] > 0) d[0]--;
    }
}
