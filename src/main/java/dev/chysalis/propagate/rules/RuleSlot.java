package dev.chysalis.propagate.rules;

/** Immutable (rule, generation) pair stored on each block state; final fields make it safe to share across threads. */
public record RuleSlot(Rule rule, int gen) {}
