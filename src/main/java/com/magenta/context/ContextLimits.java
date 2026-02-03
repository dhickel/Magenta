package com.magenta.context;

/**
 * Context window limits.
 *
 * @param maxContext Hard limit - trigger compaction when exceeded
 * @param compactThreshold Target size to compact down to
 */
public record ContextLimits(int maxContext, int compactThreshold) {
}
