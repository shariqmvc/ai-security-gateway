package com.ai.gateway.cache;

import java.util.UUID;

/**
 * Runtime view of the Phase 10 exact-response inference cache.
 */
public record InferenceCacheStats(
        boolean enabled,
        long estimatedSize,
        long hitCount,
        long missCount,
        double hitRate,
        long invalidationCount,
        long invalidatedEntries,
        UUID tenantId) {

    public static InferenceCacheStats global(
            boolean enabled,
            long estimatedSize,
            long hitCount,
            long missCount,
            long invalidationCount) {
        return new InferenceCacheStats(
                enabled,
                estimatedSize,
                hitCount,
                missCount,
                hitRate(hitCount, missCount),
                invalidationCount,
                0L,
                null);
    }

    public static InferenceCacheStats tenant(
            boolean enabled,
            long estimatedSize,
            long hitCount,
            long missCount,
            long invalidationCount,
            long invalidatedEntries,
            UUID tenantId) {
        return new InferenceCacheStats(
                enabled,
                estimatedSize,
                hitCount,
                missCount,
                hitRate(hitCount, missCount),
                invalidationCount,
                invalidatedEntries,
                tenantId);
    }

    private static double hitRate(long hits, long misses) {
        long total = hits + misses;
        return total == 0 ? 0.0d : (double) hits / total;
    }
}
