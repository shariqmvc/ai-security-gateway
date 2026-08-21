package com.ai.gateway.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TikaDocumentParserTest {

    @Test
    void shouldExtractPlainText() throws Exception {
        RagIngestionProperties properties = new RagIngestionProperties();
        properties.setMaxExtractedCharacters(1000);
        TikaDocumentParser parser = new TikaDocumentParser(properties);

        Path file = Files.createTempFile("rag-parser-test-", ".txt");
        try {
            Files.writeString(file, "# Product Documentation\n\nRefunds are processed within seven days.");

            assertTrue(parser.supports("product-guide.txt", "text/plain"));
            ParsedDocument result = parser.parse(
                    file,
                    "product-guide.txt",
                    "text/plain");

            assertTrue(result.text().contains("Refunds are processed within seven days."));
            assertNotNull(result.detectedContentType());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void shouldRejectUnsupportedExtensionWhenContentTypeIsUnknown() {
        RagIngestionProperties properties = new RagIngestionProperties();
        TikaDocumentParser parser = new TikaDocumentParser(properties);

        assertFalse(parser.supports("malware.exe", null));
    }
}
