package dev.chysalis.propagate.config;

import java.util.List;

/**
 * How one kind of thing finds and uses its surface. Categories are defined in {@code config/propagate.json};
 * the built-in ones are {@code standing}, {@code resting}, {@code hanging}, {@code mounted} and {@code attached}.
 *
 * @param name             the key in the config
 * @param anchor           where the supporting block is
 * @param needsSupport     true: vanilla-style survival rules apply and Propagate decides whether the partly
 *                         chiseled surface is good enough; false: the thing stays wherever it is put and is
 *                         only moved onto the surface
 * @param minCoverage      share (0..1) of the thing's footprint that must touch the surface to hold it
 * @param tolerance        surface parts up to this many voxels deeper than the contact point still count as touching
 * @param unionProperties  block state properties whose values share one footprint (a door keeps its height
 *                         when it opens because "open" is listed)
 * @param move             false: only relax the support rules, never move the thing
 * @param footprint        which part of the face the thing is measured against
 */
public record Category(String name, Anchor anchor, boolean needsSupport, double minCoverage, double tolerance,
                       List<String> unionProperties, boolean move, FootprintMode footprint) {

    public enum Anchor {
        /** Stands on the block below. */
        DOWN,
        /** Hangs from the block above. */
        UP,
        /** Fixed to the block behind it; "behind" comes from the {@code facing} property. */
        SIDE,
        /** Read from the block state ({@code hanging}, {@code face}, {@code attachment}, {@code half}, 6-way {@code facing} ...), else DOWN. */
        AUTO;

        public static Anchor parse(String s) {
            return switch (s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "down", "floor" -> DOWN;
                case "up", "ceiling" -> UP;
                case "side", "wall" -> SIDE;
                default -> AUTO;
            };
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum FootprintMode {
        /** The thing's own outline (all states named in union_properties together). */
        SHAPE,
        /** The centre 4x4 voxels, like vanilla's "center support". */
        CENTER,
        /** The whole face. */
        FULL;

        public static FootprintMode parse(String s) {
            return switch (s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "center", "centre" -> CENTER;
                case "full" -> FULL;
                default -> SHAPE;
            };
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
