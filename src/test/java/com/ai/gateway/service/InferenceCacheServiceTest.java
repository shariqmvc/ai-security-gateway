package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cache.CachedInferenceResponse;
import com.ai.gateway.cache.InferenceCacheService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.multimodal.MediaContent;
import com.ai.gateway.multimodal.MediaSourceType;
import com.ai.gateway.multimodal.MediaTypeKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InferenceCacheServiceTest {

    private InferenceCacheService cache;
    private AuthenticationContext tenantA;
    private AuthenticationContext tenantB;

    @BeforeEach
    void setUp() {
        cache = new InferenceCacheService(
                new ObjectMapper(),
                true,
                100,
                Duration.ofMinutes(10));

        tenantA = AuthenticationContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantCode("TENANT-A")
                .build();

        tenantB = AuthenticationContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantCode("TENANT-B")
                .build();
    }

    @Test
    void storesAndReturnsExactResponseForSameTenantAndRequest() {
        AIRequest request = request("hello", Provider.GEMINI, "gemini-3.6-flash");
        CachedInferenceResponse response =
                new CachedInferenceResponse("cached", Provider.GEMINI, "gemini-3.6-flash");

        assertNull(cache.get(tenantA, request));

        cache.put(tenantA, request, response);

        assertEquals(response, cache.get(tenantA, request));
        assertEquals(1, cache.hitCount());
        assertEquals(1, cache.missCount());
    }

    @Test
    void isolatesTenants() {
        AIRequest request = request("hello", Provider.GEMINI, "gemini-3.6-flash");

        cache.put(
                tenantA,
                request,
                new CachedInferenceResponse("tenant-a", Provider.GEMINI, "gemini-3.6-flash"));

        assertNull(cache.get(tenantB, request));
    }

    @Test
    void differentProviderOrModelDoesNotHit() {
        AIRequest request = request("hello", Provider.GEMINI, "gemini-3.6-flash");

        cache.put(
                tenantA,
                request,
                new CachedInferenceResponse("cached", Provider.GEMINI, "gemini-3.6-flash"));

        assertNull(cache.get(
                tenantA,
                request("hello", Provider.OPENAI, "gpt-5")));
    }

    @Test
    void base64MediaCanBeCached() {
        AIRequest request = request("describe", Provider.GEMINI, "gemini-3.6-flash");
        request.setMedia(List.of(MediaContent.builder()
                .type(MediaTypeKind.IMAGE)
                .sourceType(MediaSourceType.BASE64)
                .mimeType("image/png")
                .data("aGVsbG8=")
                .build()));

        CachedInferenceResponse response =
                new CachedInferenceResponse("cached-image", Provider.GEMINI, "gemini-3.6-flash");

        cache.put(tenantA, request, response);

        assertEquals(response, cache.get(tenantA, request));
    }

    @Test
    void remoteUrlMediaIsNotCached() {
        AIRequest request = request("describe", Provider.GEMINI, "gemini-3.6-flash");
        request.setMedia(List.of(MediaContent.builder()
                .type(MediaTypeKind.IMAGE)
                .sourceType(MediaSourceType.URL)
                .mimeType("image/jpeg")
                .url("https://example.com/image.jpg")
                .build()));

        cache.put(
                tenantA,
                request,
                new CachedInferenceResponse("cached", Provider.GEMINI, "gemini-3.6-flash"));

        assertNull(cache.get(tenantA, request));
    }


    @Test
    void respectsConfiguredTtl() throws InterruptedException {
        InferenceCacheService shortLivedCache = new InferenceCacheService(
                new ObjectMapper(),
                true,
                100,
                Duration.ofMillis(20));
        AIRequest request = request("ttl", Provider.OLLAMA, "llama3.1:8b");
        CachedInferenceResponse response =
                new CachedInferenceResponse("cached", Provider.OLLAMA, "llama3.1:8b");

        shortLivedCache.put(tenantA, request, response);
        assertEquals(response, shortLivedCache.get(tenantA, request));

        Thread.sleep(60L);
        assertNull(shortLivedCache.get(tenantA, request));
    }

    @Test
    void disabledCacheDoesNotStoreResponses() {
        InferenceCacheService disabledCache = new InferenceCacheService(
                new ObjectMapper(),
                false,
                100,
                Duration.ofMinutes(10));
        AIRequest request = request("disabled", Provider.OLLAMA, "llama3.1:8b");
        CachedInferenceResponse response =
                new CachedInferenceResponse("cached", Provider.OLLAMA, "llama3.1:8b");

        disabledCache.put(tenantA, request, response);

        assertNull(disabledCache.get(tenantA, request));
        assertEquals(0L, disabledCache.hitCount());
        assertEquals(0L, disabledCache.missCount());
    }

    @Test
    void invalidatesOnlyRequestedTenant() {
        AIRequest request = request("hello", Provider.OLLAMA, "llama3.1:8b");

        CachedInferenceResponse tenantAResponse =
                new CachedInferenceResponse("tenant-a", Provider.OLLAMA, "llama3.1:8b");
        CachedInferenceResponse tenantBResponse =
                new CachedInferenceResponse("tenant-b", Provider.OLLAMA, "llama3.1:8b");

        cache.put(tenantA, request, tenantAResponse);
        cache.put(tenantB, request, tenantBResponse);

        assertEquals(1L, cache.invalidateTenant(tenantA.getTenantId()));
        assertNull(cache.get(tenantA, request));
        assertEquals(tenantBResponse, cache.get(tenantB, request));
        assertEquals(1L, cache.invalidationCount());
    }

    @Test
    void invalidatingUnknownTenantDoesNotAffectExistingEntries() {
        AIRequest request = request("hello", Provider.OLLAMA, "llama3.1:8b");
        CachedInferenceResponse response =
                new CachedInferenceResponse("cached", Provider.OLLAMA, "llama3.1:8b");

        cache.put(tenantA, request, response);

        assertEquals(0L, cache.invalidateTenant(UUID.randomUUID()));
        assertEquals(response, cache.get(tenantA, request));
        assertEquals(0L, cache.invalidationCount());
    }

    @Test
    void nullTenantInvalidationIsNoOp() {
        assertEquals(0L, cache.invalidateTenant(null));
        assertEquals(0L, cache.invalidationCount());
    }

    private AIRequest request(String prompt, Provider provider, String model) {
        return AIRequest.builder()
                .prompt(prompt)
                .provider(provider)
                .model(model)
                .build();
    }
}
