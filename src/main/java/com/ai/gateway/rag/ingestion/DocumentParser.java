package com.ai.gateway.rag.ingestion;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String fileName, String contentType);

    ParsedDocument parse(Path file, String fileName, String contentType);
}
