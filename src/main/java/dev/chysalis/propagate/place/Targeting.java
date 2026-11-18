package dev.chysalis.propagate.place;

import dev.chysalis.propagate.mixin.ClipContextAccessor;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vanilla's ray only asks each cell it passes for that cell's own block. A thing lowered onto a carved
 * support reaches into the support's cell, so a ray that only crosses that cell would miss it. For
 * crosshair rays (OUTLINE) this also asks the neighbours that are moved into the crossed cell.
 * <p>
 * The hit point is then given in the thing's own cell (offset removed), so the server's
 * "is the click inside the block" check accepts it and vanilla logic sees the usual numbers.
 */
public final class Targeting {
    private static final Direction[] DIRS = Direction.values();

    private Targeting() {}

    /** A replacement hit, or null to keep vanilla's. */
    public static BlockHitResult adjust(BlockGetter level, ClipContext ctx, BlockHitResult original) {
        if (!Rules.settings().enabled || !Rules.settings().fixTargeting || Guard.active()) return null;
        if (((ClipContextAccessor) ctx).propagate$block() != ClipContext.Block.OUTLINE) return null;
        Vec3 from = ctx.getFrom(), to = ctx.getTo();

        BlockHitResult extra = BlockGetter.traverseBlocks(from, to, ctx, (c, cell) -> intoCell(level, c, cell, from, to),
                c -> null);

        BlockHitResult chosen = original;
        if (extra != null && (chosen == null || chosen.getType() == HitResult.Type.MISS
                || extra.getLocation().distanceToSqr(from) < chosen.getLocation().distanceToSqr(from))) {
            chosen = extra;
        }
        if (chosen == null || chosen.getType() == HitResult.Type.MISS) return chosen == original ? null : chosen;

        BlockPos hp = chosen.getBlockPos();
        Vec3 o = Placement.offset(level, hp, level.getBlockState(hp));
        if (o == null) return chosen == original ? null : chosen;
        return new BlockHitResult(chosen.getLocation().subtract(o), chosen.getDirection(), hp, chosen.isInside());
    }

    /** Closest hit on a neighbour moved into {@code cell}, lying inside that cell; null if none. */
    private static BlockHitResult intoCell(BlockGetter level, ClipContext ctx, BlockPos cell, Vec3 from, Vec3 to) {
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : DIRS) {
            BlockPos n = cell.relative(d);
            BlockState ns = level.getBlockState(n);
            if (!Rules.rule(ns).moves()) continue;
            Vec3 o = Placement.offset(level, n, ns);
            if (o == null) continue;
            // only things moved toward this cell (o points from n back to the cell, i.e. along -d)
            if (o.x * d.getStepX() + o.y * d.getStepY() + o.z * d.getStepZ() >= 0) continue;
            VoxelShape shape = ctx.getBlockShape(ns, level, n);
            BlockHitResult h = shape.clip(from, to, n);
            if (h == null || h.getType() == HitResult.Type.MISS) continue;
            Vec3 p = h.getLocation();
            if (!inside(p, cell)) continue;
            double dist = p.distanceToSqr(from);
            if (dist < bestD) {
                bestD = dist;
                best = h;
            }
        }
        return best;
    }

    private static boolean inside(Vec3 p, BlockPos c) {
        double e = 1e-7;
        return p.x >= c.getX() - e && p.x <= c.getX() + 1 + e
                && p.y >= c.getY() - e && p.y <= c.getY() + 1 + e
                && p.z >= c.getZ() - e && p.z <= c.getZ() + 1 + e;
    }
}
