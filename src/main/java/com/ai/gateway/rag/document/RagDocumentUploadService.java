package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.rag.ingestion.DocumentParser;
import com.ai.gateway.rag.ingestion.RagIngestionProperties;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.security.DigestInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagDocumentUploadService {

    private final RagDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;
    private final RagDocumentIngestionProcessor ingestionProcessor;
    private final RagIngestionProperties properties;
    private final DocumentParser documentParser;

    @Transactional
    public DocumentResponse upload(UUID tenantId,
                                   UUID knowledgeBaseId,
                                   MultipartFile file) {
        tenantAccessGuard.requireAccess(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        validateFile(file);

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new com.ai.gateway.rag.knowledge.KnowledgeBaseNotFoundException(
                        knowledgeBaseId));

        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot upload a document to an archived knowledge base.");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        if (!documentParser.supports(fileName, file.getContentType())) {
            throw new BusinessException(
                    "Unsupported document type: " + fileName);
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(properties.tempDirectoryPath());
            temporaryFile = Files.createTempFile(
                    properties.tempDirectoryPath(),
                    "rag-",
                    ".upload");

            file.transferTo(temporaryFile);
            long size = Files.size(temporaryFile);
            if (size > properties.getMaxFileSizeBytes()) {
                throw new BusinessException(
                        "Document exceeds maximum allowed size of "
                                + properties.getMaxFileSizeBytes() + " bytes.");
            }

            String checksum = sha256(temporaryFile);
            if (documentRepository.existsByKnowledgeBase_IdAndChecksumSha256(
                    knowledgeBaseId, checksum)) {
                throw new DocumentAlreadyExistsException(knowledgeBaseId, checksum);
            }

            LocalDateTime now = LocalDateTime.now();
            RagDocument document = RagDocument.builder()
                    .id(UUID.randomUUID())
                    .knowledgeBase(knowledgeBase)
                    .fileName(fileName)
                    .contentType(file.getContentType())
                    .fileSizeBytes(size)
                    .checksumSha256(checksum)
                    .status(DocumentStatus.REGISTERED)
                    .chunkCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            RagDocument saved = documentRepository.save(document);
            Path committedFile = temporaryFile;
            registerAfterCommit(
                    () -> ingestionProcessor.processAsync(
                            tenantId,
                            saved.getId(),
                            committedFile,
                            fileName,
                            file.getContentType()),
                    committedFile);

            return toResponse(saved);
        } catch (IOException ex) {
            deleteQuietly(temporaryFile);
            throw new BusinessException(
                    "Unable to stage uploaded document: " + fileName +" "+ex);
        } catch (RuntimeException ex) {
            deleteQuietly(temporaryFile);
            throw ex;
        }
    }

    private void registerAfterCommit(Runnable action, Path temporaryFile) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            deleteQuietly(temporaryFile);
                        }
                    }
                });
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Document file is required.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(
                    "Document exceeds maximum allowed size of "
                            + properties.getMaxFileSizeBytes() + " bytes.");
        }
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException("Document file name is required.");
        }
        try {
            String fileName = Paths.get(originalFileName)
                    .getFileName()
                    .toString()
                    .trim();
            if (fileName.isBlank() || fileName.contains("..")) {
                throw new BusinessException("Invalid document file name.");
            }
            return fileName;
        } catch (RuntimeException ex) {
            throw new BusinessException("Invalid document file name." +ex);
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInput.read(buffer) != -1) {
                    // DigestInputStream updates the digest as bytes are read.
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; the async processor also cleans up after use.
        }
    }

    private DocumentResponse toResponse(RagDocument entity) {
        return DocumentResponse.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBase().getId())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .fileSizeBytes(entity.getFileSizeBytes())
                .checksumSha256(entity.getChecksumSha256())
                .status(entity.getStatus())
                .chunkCount(entity.getChunkCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
