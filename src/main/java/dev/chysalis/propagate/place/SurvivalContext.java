package dev.chysalis.propagate.place;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which categorized thing is currently checking whether it may stay / be placed, per thread.
 * <p>
 * Vanilla asks the <em>support</em> ({@code isFaceSturdy}) without saying who is asking. Propagate only
 * relaxes that answer while one of its things is asking (pushed around {@code BlockState#canSurvive} and
 * {@code BlockState#updateShape}), so fences, redstone, mob spawning etc. keep the vanilla answer.
 */
public final class SurvivalContext {
    public record Frame(BlockState state, BlockPos pos, net.minecraft.world.level.BlockGetter level) {}

    private static final int MAX = 16;
    private static final ThreadLocal<Frame[]> STACK = ThreadLocal.withInitial(() -> new Frame[MAX + 1]);
    private static final ThreadLocal<int[]> SIZE = ThreadLocal.withInitial(() -> new int[1]);

    private SurvivalContext() {}

    public static void push(BlockState state, BlockPos pos, net.minecraft.world.level.BlockGetter level) {
        int[] n = SIZE.get();
        if (n[0] >= MAX) n[0] = 0; // leaked frames (an exception skipped a pop): start over
        STACK.get()[n[0]++] = new Frame(state, pos.immutable(), level);
    }

    /** Pops the top frame if it belongs to this call (tolerates a push that never happened). */
    public static void pop(BlockState state, BlockPos pos) {
        int[] n = SIZE.get();
        if (n[0] == 0) return;
        Frame[] s = STACK.get();
        Frame top = s[n[0] - 1];
        if (top.state() == state && top.pos().equals(pos)) {
            s[--n[0]] = null;
        }
    }

    public static Frame top() {
        int n = SIZE.get()[0];
        return n == 0 ? null : STACK.get()[n - 1];
    }
}
