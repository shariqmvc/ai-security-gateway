# AegisAI Phase 3 Embedding Test

AegisAI is a multi-tenant AI gateway.

The gateway provides provider abstraction, model routing,
security controls, cost management, governance, observability,
and retrieval augmented generation capabilities.

This document is being uploaded to validate Phase 3 embedding
infrastructure.

Phase 3 converts READY_FOR_EMBEDDING document chunks into
provider-generated embedding vectors.

The vectors are stored in PostgreSQL using pgvector.

The embedding provider for this test is Ollama.

The embedding model is nomic-embed-text.

