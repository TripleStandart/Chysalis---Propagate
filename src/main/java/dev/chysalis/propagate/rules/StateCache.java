package dev.chysalis.propagate.rules;

/**
 * Per-BlockState cache slots, added to every block state by {@code BlockStateBaseMixin}. Keeps the hot
 * shape hooks to a field read for the 99% of states Propagate never touches.
 */
public interface StateCache {
    /** The cached {@link Rule}, or null if missing or from an older generation. */
    Rule propagate$rule(int generation);

    void propagate$setRule(Rule rule, int generation);

    /** 1 = support, 0 = not, -1 = unknown / stale. */
    int propagate$support(int generation);

    void propagate$setSupport(boolean support, int generation);
}
