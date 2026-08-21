package com.ai.gateway.rag.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void shouldNormalizeLineEndingsWhitespaceAndUnicode() {
        String result = normalizer.normalize("\u0065\u0301  test\r\n\r\n\r\nnext  \r\n");

        assertEquals("é  test\n\nnext", result);
    }

    @Test
    void shouldRejectEmptyText() {
        assertThrows(
                DocumentIngestionException.class,
                () -> normalizer.normalize(" \n\t "));
    }
}
