package com.ai.gateway.rag.ingestion;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class TextNormalizer {

    private static final Pattern THREE_OR_MORE_BLANK_LINES =
            Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_HORIZONTAL_WHITESPACE =
            Pattern.compile("[ \\t]+(?=\\n)");

    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            throw new DocumentIngestionException("Extracted document text is empty.");
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "");

        normalized = TRAILING_HORIZONTAL_WHITESPACE
                .matcher(normalized)
                .replaceAll("");
        normalized = THREE_OR_MORE_BLANK_LINES
                .matcher(normalized)
                .replaceAll("\n\n");

        normalized = normalized.trim();
        if (normalized.isBlank()) {
            throw new DocumentIngestionException("Extracted document text is empty.");
        }
        return normalized;
    }
}
