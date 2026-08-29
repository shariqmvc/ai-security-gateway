package com.ai.gateway.service;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.multimodal.*;
import com.ai.gateway.core.provider.openai.dto.OpenAIRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultimodalProviderPayloadTest {

    @Test
    void requestCarriesMediaWithoutBreakingTextCompatibility() {
        AIRequest request = AIRequest.builder()
                .prompt("What is in the image?")
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.BASE64)
                        .mimeType("image/png")
                        .data("aGVsbG8=")
                        .build()))
                .build();

        OpenAIRequest payload = OpenAIRequest.builder()
                .model("test")
                .input(request.getPrompt())
                .build();

        assertEquals("What is in the image?", payload.getInput());
        assertEquals(1, request.getMedia().size());
    }
}
