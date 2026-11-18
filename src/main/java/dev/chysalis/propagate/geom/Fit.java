package dev.chysalis.propagate.geom;

/**
 * Where a thing comes to rest against a support.
 *
 * @param offset   how far (in blocks) it moves toward the support: the smallest surface depth under its
 *                 footprint, so it touches the highest point and never cuts into the support
 * @param coverage share of the footprint (0..1) whose surface lies within the tolerance of that point
 * @param contact  false if there is no material under the footprint at all (offset is then 0)
 */
public record Fit(double offset, double coverage, boolean contact) {
    public static final Fit NONE = new Fit(0.0, 0.0, false);

    /**
     * @param toleranceVoxels surface parts up to this many voxels deeper than the contact point still
     *                        count as touching (so a slightly uneven carved top still holds a door)
     */
    public static Fit of(DepthMap map, Footprint fp, double toleranceVoxels) {
        float min = DepthMap.EMPTY;
        for (int i = 0; i < 256; i++) {
            if (fp.has(i)) min = Math.min(min, map.depthAt(i));
        }
        if (min >= DepthMap.EMPTY) return NONE;
        double limit = min + toleranceVoxels + 1e-6;
        int touching = 0;
        for (int i = 0; i < 256; i++) {
            if (fp.has(i) && map.depthAt(i) <= limit) touching++;
        }
        double offset = min / 16.0;
        if (offset < 1e-6) offset = 0.0;
        return new Fit(offset, touching / (double) fp.count(), true);
    }

    /** True if this fit holds the thing up (enough of it touches, and it sinks no deeper than allowed). */
    public boolean supports(double minCoverage, double maxOffset) {
        return contact && coverage + 1e-9 >= minCoverage && offset <= maxOffset + 1e-9;
    }

    /** The offset actually applied: things that would sink deeper than allowed stay where vanilla puts them. */
    public double appliedOffset(double maxOffset) {
        return contact && offset <= maxOffset + 1e-9 ? offset : 0.0;
    }
}
