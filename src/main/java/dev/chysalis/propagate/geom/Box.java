package dev.chysalis.propagate.geom;

/** An axis-aligned box in block units (0..1 inside the block; may stick out for odd modded shapes). */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public static Box voxels(int x0, int y0, int z0, int x1, int y1, int z1) {
        return new Box(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    public double min(int axis) {
        return axis == 0 ? minX : axis == 1 ? minY : minZ;
    }

    public double max(int axis) {
        return axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
    }
}
