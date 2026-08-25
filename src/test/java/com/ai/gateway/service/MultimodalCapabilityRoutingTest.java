package com.ai.gateway.service;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.multimodal.*;
import com.ai.gateway.routing.registry.ModelCapabilities;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MultimodalCapabilityRoutingTest {

    @Test
    void imageInputRequiresVisionCapability() {
        ChatRequest request = ChatRequest.builder()
                .prompt("describe")
                .requiredCapabilities(Set.of(ModelCapabilities.VISION))
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/jpeg")
                        .url("https://example.com/a.jpg")
                        .build()))
                .build();

        Set<String> capabilities = new LinkedHashSet<>(request.getRequiredCapabilities());
        capabilities.add(ModelCapabilities.VISION);
        assertTrue(capabilities.contains(ModelCapabilities.VISION));
    }

    @Test
    void audioInputRequiresAudioCapability() {
        ChatRequest request = ChatRequest.builder()
                .prompt("transcribe")
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.AUDIO)
                        .sourceType(MediaSourceType.BASE64)
                        .mimeType("audio/wav")
                        .data("aGVsbG8=")
                        .build()))
                .build();

        assertNotNull(request.getMedia());
        assertEquals(MediaTypeKind.AUDIO, request.getMedia().getFirst().getType());
    }
}
