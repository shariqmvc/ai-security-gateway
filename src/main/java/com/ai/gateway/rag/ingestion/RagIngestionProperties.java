package com.ai.gateway.rag.ingestion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.ingestion")
public class RagIngestionProperties {

    private long maxFileSizeBytes = 20L * 1024L * 1024L;
    private int maxExtractedCharacters = 2_000_000;
    private int chunkSizeTokens = 512;
    private int chunkOverlapTokens = 64;
    private int maxChunks = 10_000;
    private String tempDirectory = Paths.get(
            System.getProperty("java.io.tmpdir"), "aegisai-rag").toString();

    private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "docx", "md", "markdown", "txt", "html", "htm", "json", "csv"));

    public Path tempDirectoryPath() {
        return Paths.get(tempDirectory).toAbsolutePath().normalize();
    }
}
