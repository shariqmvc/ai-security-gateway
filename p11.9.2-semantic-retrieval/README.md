# P11.9.2 — Semantic Security Retrieval

This package implements the offline semantic-security retrieval layer for
AIRouter P11.9.

Architecture:

    security corpus
        |
        v
    Ollama / nomic-embed-text
        |
        v
    768-dimensional embeddings
        |
        v
    PostgreSQL + PGVector
        |
        v
    cosine nearest-neighbor retrieval
        |
        v
    predicted security category

IMPORTANT:
- This is an OFFLINE evaluation/indexing component.
- It does NOT modify the AIRouter production request path.
- It does NOT block requests.
- It does NOT change PersonalFirewallClient.
- The independent test set must not be indexed for threshold tuning.

Expected existing infrastructure:
- Ollama available at http://localhost:11434
- nomic-embed-text installed
- PostgreSQL with pgvector
- AIRouter semantic corpus at data/semantic_security_v1/

The SQL creates a dedicated table:
  personal_security_semantic_examples

This table is intentionally separate from inference/RAG application tables.

## Path note

The scripts expect the P11.9.1 corpus to remain in the AIRouter project root:

    <project>\data\semantic_security_v1\

The P11.9.2 package itself can be placed at:

    <project>\p11.9.2-semantic-retrieval\

The scripts resolve the corpus relative to the AIRouter project root.
