package com.ai.gateway.rag.knowledge;

/**
 * Raised when a knowledge base is not visible within the authenticated
 * tenant's authoritative schema.
 *
 * <p>The exception intentionally does not distinguish between a nonexistent
 * ID and an ID owned by another tenant. This preserves tenant isolation
 * without leaking cross-tenant resource existence.</p>
 */
public class KnowledgeBaseNotFoundException extends RuntimeException {

    public KnowledgeBaseNotFoundException(java.util.UUID knowledgeBaseId) {
        super("Knowledge base not found: " + knowledgeBaseId);
    }
}
