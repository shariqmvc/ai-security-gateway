package com.ai.gateway.rag.document.dto;

import com.ai.gateway.rag.document.DocumentStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class DocumentResponse {
    private UUID id;
    private UUID knowledgeBaseId;
    private String fileName;
    private String contentType;
    private Long fileSizeBytes;
    private String checksumSha256;
    private DocumentStatus status;
    private int chunkCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
