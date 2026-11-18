package dev.chysalis.propagate.client;

import net.minecraft.world.phys.Vec3;

/** The offset of the block whose animateTick is running (render thread only). */
public final class ParticleShift {
    private static double x, y, z;

    private ParticleShift() {}

    public static void set(Vec3 o) {
        if (o == null) {
            x = y = z = 0;
        } else {
            x = o.x;
            y = o.y;
            z = o.z;
        }
    }

    public static double x() { return x; }
    public static double y() { return y; }
    public static double z() { return z; }
}
