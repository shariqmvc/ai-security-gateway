package com.ai.gateway.cache;

import com.ai.gateway.enums.Provider;

/**
 * Provider inference result retained by the exact-response cache.
 * The raw provider response is stored so request-specific PII restoration
 * can still run with the current request correlation/token-vault context.
 */
public record CachedInferenceResponse(
        String response,
        Provider provider,
        String model) {
}
