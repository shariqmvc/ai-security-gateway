package com.ai.gateway.service.impl;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.personal.inference.PersonalTokenVaultService;
import com.ai.gateway.service.PIIDetectionService;
import com.ai.gateway.service.TokenVaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AttachedDocumentExtractionServiceTest {

    private PIIDetectionService piiDetectionService;
    private TokenVaultService tokenVaultService;
    private PersonalTokenVaultService personalTokenVaultService;
    private AttachedDocumentExtractionService service;

    @BeforeEach
    void setUp() {
        piiDetectionService = mock(PIIDetectionService.class);
        tokenVaultService = mock(TokenVaultService.class);
        personalTokenVaultService = mock(PersonalTokenVaultService.class);

        when(piiDetectionService.mask(anyString()))
                .thenAnswer(invocation -> MaskingResult.builder()
                        .maskedPrompt(invocation.getArgument(0))
                        .detectedValues(List.of())
                        .build());

        service = new AttachedDocumentExtractionService(
                piiDetectionService,
                tokenVaultService,
                personalTokenVaultService);
    }

    @Test
    void materializesAllDocumentsAndRetainsImagesForNonGeminiProvider() {
        MediaContent documentA = document("a.txt", "Document A content");
        MediaContent documentB = document("b.txt", "Document B content");
        MediaContent image = MediaContent.builder()
                .type(MediaTypeKind.IMAGE)
                .sourceType(MediaSourceType.BASE64)
                .mimeType("image/png")
                .data(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}))
                .build();

        AIRequest request = AIRequest.builder()
                .provider(Provider.OLLAMA)
                .model("qwen2.5vl:3b")
                .prompt("Analyze every attached file.")
                .media(List.of(documentA, image, documentB))
                .build();

        AIRequest result = service.materializeUnsupportedDocuments(null, null, null, request);

        assertEquals(1, result.getMedia().size());
        assertEquals(MediaTypeKind.IMAGE, result.getMedia().getFirst().getType());
        assertTrue(result.getPrompt().contains("Document A content"));
        assertTrue(result.getPrompt().contains("Document B content"));
        assertTrue(result.getPrompt().contains("a.txt"));
        assertTrue(result.getPrompt().contains("b.txt"));
    }

    @Test
    void keepsAllDocumentMediaForGeminiNativeDocumentInput() {
        MediaContent documentA = document("a.txt", "Document A content");
        MediaContent documentB = document("b.txt", "Document B content");

        AIRequest request = AIRequest.builder()
                .provider(Provider.GEMINI)
                .model("gemini-2.5-flash")
                .prompt("Analyze both documents.")
                .media(List.of(documentA, documentB))
                .build();

        AIRequest result = service.materializeUnsupportedDocuments(null, null, null, request);

        assertEquals(2, result.getMedia().size());
        assertEquals(MediaTypeKind.DOCUMENT, result.getMedia().get(0).getType());
        assertEquals(MediaTypeKind.DOCUMENT, result.getMedia().get(1).getType());
        assertEquals("Analyze both documents.", result.getPrompt());
        verifyNoInteractions(piiDetectionService);
    }

    private MediaContent document(String name, String text) {
        return MediaContent.builder()
                .type(MediaTypeKind.DOCUMENT)
                .sourceType(MediaSourceType.BASE64)
                .mimeType("text/plain")
                .fileName(name)
                .data(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
