package com.ai.gateway.service;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.multimodal.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultimodalRequestValidationTest {

    private final MultimodalRequestValidator validator = new MultimodalRequestValidator();

    @Test
    void acceptsBase64Image() {
        ChatRequest request = ChatRequest.builder()
                .prompt("Describe this image")
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.BASE64)
                        .mimeType("image/png")
                        .data("aGVsbG8=")
                        .build()))
                .build();

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void rejectsMismatchedMimeType() {
        ChatRequest request = ChatRequest.builder()
                .prompt("describe")
                .media(List.of(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("audio/mpeg")
                        .url("https://example.com/a.mp3")
                        .build()))
                .build();

        assertThrows(MultimodalValidationException.class, () -> validator.validate(request));
    }

    @Test
    void rejectsMoreThanEightMediaItems() {
        var items = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/png")
                        .url("https://example.com/" + i + ".png")
                        .build())
                .toList();

        ChatRequest request = ChatRequest.builder().prompt("describe").media(items).build();
        assertThrows(MultimodalValidationException.class, () -> validator.validate(request));
    }
    @Test
    void rejectsInvalidBase64() {

        ChatRequest request = ChatRequest.builder()
                .prompt("Describe this image")
                .media(List.of(
                        MediaContent.builder()
                                .type(MediaTypeKind.IMAGE)
                                .sourceType(MediaSourceType.BASE64)
                                .mimeType("image/png")
                                .data("THIS_IS_NOT_VALID_BASE64")
                                .build()
                ))
                .build();

        MultimodalValidationException exception =
                assertThrows(
                        MultimodalValidationException.class,
                        () -> validator.validate(request)
                );

        assertEquals(
                "Invalid BASE64 media data.",
                exception.getMessage()
        );
    }
}
