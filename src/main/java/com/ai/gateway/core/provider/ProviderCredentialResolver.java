package com.ai.gateway.core.provider;

import com.ai.gateway.core.model.Provider;

import java.util.UUID;

/**
 * Product-neutral credential resolution boundary.
 *
 * Core provider adapters depend only on this contract; Personal, Business,
 * or another product supplies the credential source through its own adapter.
 */
public interface ProviderCredentialResolver {
    String resolveApiKey(UUID accountId, Provider provider);
}
