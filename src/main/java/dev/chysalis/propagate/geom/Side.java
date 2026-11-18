package dev.chysalis.propagate.geom;

/**
 * The six sides, in the same order as Minecraft's {@code Direction} (DOWN, UP, NORTH, SOUTH, WEST, EAST),
 * so {@code Side.values()[direction.ordinal()]} converts.
 */
public enum Side {
    DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1), WEST(0, -1), EAST(0, 1);

    /** 0 = x, 1 = y, 2 = z. */
    public final int axis;
    /** -1 or +1. */
    public final int sign;

    Side(int axis, int sign) {
        this.axis = axis;
        this.sign = sign;
    }

    public Side opposite() {
        return values()[ordinal() ^ 1];
    }

    /** First in-plane axis of a face across {@code axis}: y -> x, x -> y, z -> x. */
    public static int uAxis(int axis) {
        return axis == 0 ? 1 : 0;
    }

    /** Second in-plane axis: y -> z, x -> z, z -> y. */
    public static int vAxis(int axis) {
        return axis == 2 ? 1 : 2;
    }
}
