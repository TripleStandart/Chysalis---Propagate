package dev.chysalis.propagate.rules;

import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.geom.Side;
import net.minecraft.core.Direction;

/**
 * What Propagate does with one block state.
 *
 * @param category null = nothing
 * @param anchor   direction from the thing to its support
 * @param link     how the parts of a multi-block thing share one offset
 * @param partner  for {@link Link#PAIR}: where the other part is (null = search both sides)
 * @param why      for /propagate inspect: which config entry decided
 */
public record Rule(Category category, Side anchor, Link link, Direction[] partner, String why) {
    public enum Link {
        /** A single block. */
        NONE,
        /** Lower half of a two-high thing (door, tall plant): measured itself. */
        LOWER,
        /** Upper half: uses the lower half's offset. */
        UPPER,
        /** Bed halves, double chests: both use the smaller of their two offsets. */
        PAIR
    }

    public static final Rule NONE = new Rule(null, null, Link.NONE, null, "not a thing Propagate handles");

    public static Rule none(String why) {
        return new Rule(null, null, Link.NONE, null, why);
    }

    public boolean isNone() {
        return category == null;
    }

    /** True if the thing is moved onto its surface. */
    public boolean moves() {
        return category != null && category.move();
    }
}
