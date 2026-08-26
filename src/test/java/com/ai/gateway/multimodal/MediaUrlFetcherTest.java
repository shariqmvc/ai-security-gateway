package com.ai.gateway.multimodal;

import com.ai.gateway.config.RemoteMediaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaUrlFetcherTest {

    @Test
    void rejectsLocalhostBeforeNetworkAccess() {
        RemoteMediaProperties properties = new RemoteMediaProperties();
        MediaUrlFetcher fetcher = new MediaUrlFetcher(properties);

        MediaInputException ex = assertThrows(
                MediaInputException.class,
                () -> fetcher.fetch(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/jpeg")
                        .url("https://127.0.0.1/image.jpg")
                        .build()));

        assertTrue(ex.getMessage().contains("private or local network"));
    }

    @Test
    void rejectsNonHttpsByDefault() {
        RemoteMediaProperties properties = new RemoteMediaProperties();
        MediaUrlFetcher fetcher = new MediaUrlFetcher(properties);

        MediaInputException ex = assertThrows(
                MediaInputException.class,
                () -> fetcher.fetch(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/jpeg")
                        .url("http://example.com/image.jpg")
                        .build()));

        assertTrue(ex.getMessage().contains("Only HTTPS"));
    }

    @Test
    void rejectsNonDefaultPort() {
        RemoteMediaProperties properties = new RemoteMediaProperties();
        MediaUrlFetcher fetcher = new MediaUrlFetcher(properties);

        MediaInputException ex = assertThrows(
                MediaInputException.class,
                () -> fetcher.fetch(MediaContent.builder()
                        .type(MediaTypeKind.IMAGE)
                        .sourceType(MediaSourceType.URL)
                        .mimeType("image/jpeg")
                        .url("https://example.com:8443/image.jpg")
                        .build()));

        assertTrue(ex.getMessage().contains("Non-default"));
    }
}
