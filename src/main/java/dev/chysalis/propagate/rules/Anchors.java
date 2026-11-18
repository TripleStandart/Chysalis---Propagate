package dev.chysalis.propagate.rules;

import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.geom.Side;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Reads from a block state where a thing's support is and how its parts belong together. Works on
 * property names and value types (not on classes), so modded blocks that reuse vanilla properties
 * (a modded lantern with "hanging", a modded bed with a BedPart property) are understood too.
 */
public final class Anchors {
    private Anchors() {}

    /** Direction from the thing to its support, or null if the state does not say. */
    public static Side resolve(BlockState st, Category.Anchor mode) {
        return switch (mode) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case SIDE -> {
                Direction f = direction(st, "facing");
                yield f != null && f.getAxis().isHorizontal() ? side(f.getOpposite()) : null;
            }
            case AUTO -> auto(st);
        };
    }

    private static Side auto(BlockState st) {
        Object hanging = value(st, "hanging");
        if (hanging instanceof Boolean b) return b ? Side.UP : Side.DOWN;

        Object face = value(st, "face");
        if (face instanceof AttachFace af) {
            return switch (af) {
                case FLOOR -> Side.DOWN;
                case CEILING -> Side.UP;
                case WALL -> {
                    Direction f = direction(st, "facing");
                    yield f != null ? side(f.getOpposite()) : null;
                }
            };
        }

        Object attachment = value(st, "attachment");
        if (attachment instanceof BellAttachType bt) {
            // Wall bells hang between posts; there is no single surface to rest against.
            return bt == BellAttachType.FLOOR ? Side.DOWN : bt == BellAttachType.CEILING ? Side.UP : null;
        }

        Object half = value(st, "half");
        if (half instanceof Half h) return h == Half.TOP ? Side.UP : Side.DOWN;

        Direction tip = direction(st, "vertical_direction");
        if (tip != null && tip.getAxis().isVertical()) return tip == Direction.UP ? Side.DOWN : Side.UP;

        Property<?> facingProp = property(st, "facing");
        if (facingProp != null && facingProp.getPossibleValues().contains(Direction.UP)) {
            Direction f = direction(st, "facing");
            if (f != null) return side(f.getOpposite()); // end rods, lightning rods, amethyst: grow away from the support
        }
        return Side.DOWN;
    }

    /** How the parts of a multi-block thing are linked. */
    public static Rule.Link link(BlockState st) {
        for (Property<?> p : st.getProperties()) {
            Object v = st.getValue(p);
            if (v instanceof DoubleBlockHalf h) return h == DoubleBlockHalf.UPPER ? Rule.Link.UPPER : Rule.Link.LOWER;
            if (v instanceof BedPart) return Rule.Link.PAIR;
            if (v instanceof ChestType ct && ct != ChestType.SINGLE) return Rule.Link.PAIR;
        }
        return Rule.Link.NONE;
    }

    /** Where the other half of a bed / double chest may be (checked against the world later). */
    public static Direction[] partner(BlockState st) {
        Direction f = direction(st, "facing");
        if (f == null || !f.getAxis().isHorizontal()) return null;
        for (Property<?> p : st.getProperties()) {
            Object v = st.getValue(p);
            if (v instanceof BedPart bp) return new Direction[]{bp == BedPart.FOOT ? f : f.getOpposite()};
            if (v instanceof ChestType ct && ct != ChestType.SINGLE) {
                // Either side; the world check picks the one holding the matching half.
                return new Direction[]{f.getClockWise(), f.getCounterClockWise()};
            }
        }
        return null;
    }

    public static Side side(Direction d) {
        return Side.values()[d.ordinal()];
    }

    public static Direction direction(Side s) {
        return Direction.values()[s.ordinal()];
    }

    public static Property<?> property(BlockState st, String name) {
        for (Property<?> p : st.getProperties()) if (p.getName().equals(name)) return p;
        return null;
    }

    private static Object value(BlockState st, String name) {
        Property<?> p = property(st, name);
        return p == null ? null : st.getValue(p);
    }

    private static Direction direction(BlockState st, String name) {
        return value(st, name) instanceof Direction d ? d : null;
    }
}
