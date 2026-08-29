package com.ai.gateway.core.provider.gemini;

import com.ai.gateway.config.GeminiConfig;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.multimodal.MediaUrlFetcher;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.gemini.dto.Candidate;
import com.ai.gateway.core.provider.gemini.dto.CandidateContent;
import com.ai.gateway.core.provider.gemini.dto.GeminiPart;
import com.ai.gateway.core.provider.gemini.dto.GeminiResponse;
import com.ai.gateway.core.provider.gemini.dto.UsageMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GeminiProviderTest {

    @Test
    void translatesUrlMediaToGeminiInlineData() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GeminiConfig config = new GeminiConfig();
        config.setModel("gemini-3.6-flash");
        config.setUrl("https://generativelanguage.googleapis.com");
        config.setApiKey("test-key");
        PerformanceLogger logger = mock(PerformanceLogger.class);
        MediaUrlFetcher fetcher = mock(MediaUrlFetcher.class);

        GeminiProvider provider = new GeminiProvider(restTemplate, config, logger, fetcher);
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", "https://generativelanguage.googleapis.com");
        ReflectionTestUtils.setField(provider, "model", "gemini-3.6-flash");

        when(fetcher.fetch(any(MediaContent.class)))
                .thenReturn(new MediaUrlFetcher.ResolvedMedia("image/jpeg", new byte[]{1, 2, 3}, "AQID"));

        GeminiResponse response = new GeminiResponse(
                List.of(new Candidate(CandidateContent.builder()
                        .parts(List.of(GeminiPart.builder().text("A cat").build()))
                        .build())),
                new UsageMetadata(1, 2, 3, 0),
                "gemini-3.6-flash",
                "provider-response-1");

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(),
                eq(GeminiResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        AIRequest request = AIRequest.builder()
                .provider(com.ai.gateway.core.model.Provider.GEMINI)
                .model("gemini-3.6-flash")
                .prompt("Describe this image")
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/jpeg")
                        .url("https://example.com/cat.jpg")
                        .build()))
                .build();

        AIResponse result = provider.chat(request);

        assertEquals("A cat", result.getResponse());
        verify(fetcher).fetch(any(MediaContent.class));
        verify(restTemplate).exchange(
                any(String.class), eq(HttpMethod.POST), argThat(entity -> {
                    Object body = entity.getBody();
                    if (!(body instanceof com.ai.gateway.core.provider.gemini.dto.GeminiRequest geminiRequest)) {
                        return false;
                    }
                    GeminiPart mediaPart = geminiRequest.getContents().getFirst().getParts().get(1);
                    return mediaPart.getInlineData() != null
                            && "image/jpeg".equals(mediaPart.getInlineData().getMimeType())
                            && "AQID".equals(mediaPart.getInlineData().getData());
                }), eq(GeminiResponse.class));
    }
}
