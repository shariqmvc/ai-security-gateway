package com.ai.gateway.cache;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.multimodal.MediaContent;
import com.ai.gateway.multimodal.MediaSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 10 exact-response inference cache.
 *
 * This is deliberately a local, bounded cache for the first Phase 10 slice.
 * It is fail-open and tenant-isolated. Redis/distributed caching can be added
 * behind this abstraction without changing GatewayServiceImpl.
 */
@Slf4j
@Service
public class InferenceCacheService {

    private static final String KEY_VERSION = "v1";

    private final boolean enabled;
    private final Cache<String, CachedInferenceResponse> cache;
    private final ObjectMapper objectMapper;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public InferenceCacheService(
            ObjectMapper objectMapper,
            @Value("${gateway.cache.exact.enabled:true}") boolean enabled,
            @Value("${gateway.cache.exact.maximum-size:10000}") long maximumSize,
            @Value("${gateway.cache.exact.ttl:10m}") Duration ttl) {

        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1L, maximumSize))
                .expireAfterWrite(ttl)
                .build();
    }

    public CachedInferenceResponse get(
            AuthenticationContext auth,
            AIRequest request) {

        if (!isCacheable(auth, request)) {
            return null;
        }

        try {
            String key = buildKey(auth.getTenantId(), request);
            CachedInferenceResponse value = cache.getIfPresent(key);
            if (value == null) {
                misses.incrementAndGet();
                return null;
            }

            hits.incrementAndGet();
            return value;
        } catch (RuntimeException ex) {
            // Cache must never become a request availability dependency.
            log.warn("Inference cache lookup failed; continuing without cache: {}", ex.getMessage());
            misses.incrementAndGet();
            return null;
        }
    }

    public void put(
            AuthenticationContext auth,
            AIRequest request,
            CachedInferenceResponse response) {

        if (!isCacheable(auth, request) || response == null || response.response() == null) {
            return;
        }

        try {
            cache.put(buildKey(auth.getTenantId(), request), response);
        } catch (RuntimeException ex) {
            // Cache write failure must never fail a successful provider request.
            log.warn("Inference cache write failed; continuing without cache: {}", ex.getMessage());
        }
    }

    /**
     * Removes every exact-response cache entry owned by the supplied tenant.
     *
     * @return number of entries removed from this local cache
     */
    public long invalidateTenant(UUID tenantId) {
        if (tenantId == null) {
            return 0L;
        }

        // Caffeine's first implementation is intentionally bounded and local.
        // A distributed tenant-indexed invalidation strategy will be introduced
        // with the Redis implementation.
        String prefix = "aegis:cache:" + KEY_VERSION + ":tenant:" + tenantId + ":";
        long removed = cache.asMap().keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .count();
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        if (removed > 0) {
            invalidations.incrementAndGet();
            log.info("Inference cache tenant invalidated: tenantId={}, entriesRemoved={}", tenantId, removed);
        }
        return removed;
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public long invalidationCount() {
        return invalidations.get();
    }

    public InferenceCacheStats stats() {
        return InferenceCacheStats.global(
                enabled,
                estimatedSize(),
                hitCount(),
                missCount(),
                invalidationCount());
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isCacheable(AuthenticationContext auth, AIRequest request) {
        return enabled
                && auth != null
                && auth.getTenantId() != null
                && request != null
                && request.getProvider() != null
                && request.getModel() != null
                && !request.getModel().isBlank()
                && request.getPrompt() != null
                && mediaIsCacheSafe(request);
    }

    private boolean mediaIsCacheSafe(AIRequest request) {
        if (request.getMedia() == null || request.getMedia().isEmpty()) {
            return true;
        }
        for (MediaContent media : request.getMedia()) {
            if (media == null || media.getSourceType() != MediaSourceType.BASE64) {
                return false;
            }
        }
        return true;
    }

    private String buildKey(UUID tenantId, AIRequest request) {
        try {
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("provider", providerName(request.getProvider()));
            canonical.put("model", request.getModel());
            canonical.put("prompt", request.getPrompt());
            canonical.put("media", request.getMedia());

            byte[] payload = objectMapper.writeValueAsBytes(canonical);
            return "aegis:cache:" + KEY_VERSION
                    + ":tenant:" + tenantId
                    + ":exact:" + sha256(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build inference cache key", ex);
        }
    }

    private String providerName(Provider provider) {
        return provider == null ? "" : provider.name();
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
