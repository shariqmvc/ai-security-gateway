package com.ai.gateway.rag.document;

import java.util.UUID;

public class DocumentAlreadyExistsException extends RuntimeException {
    public DocumentAlreadyExistsException(UUID knowledgeBaseId, String checksum) {
        super("A document with the same checksum already exists in knowledge base "
                + knowledgeBaseId + ": " + checksum);
    }
}
