# AegisAI Phase 4 — RAG Complete API Test Guide

## Preconditions

- Start the gateway on `http://localhost:8080`.
- Import `AegisAI-Phase4-RAG.postman_collection.json`.
- Import `AegisAI-Phase4-RAG.postman_environment.json`.
- Set `tenantBApiKey` and `tenantAApiKey` in the Postman environment.
- Set `knowledgeBaseId` to an active PGVECTOR knowledge base for the selected tenant. The supplied environment contains the current Phase-4 test KB as the default.
- For the prompt-injection upload test, set `promptInjectionFilePath` to the included `Tenant-RAG-Prompt-Injection-Test.md` file path.

## Acceptance sequence

1. VECTOR search — HTTP 200; results are relevant.
2. KEYWORD search — HTTP 200.
3. HYBRID search — HTTP 200; query transformation works.
4. HYBRID_RERANKED — HTTP 200; final `minScore` is enforced after reranking and `topK` is respected.
5. `/api/chat` with RAG and `contextTokenBudget=256` — HTTP 200; `data.rag.estimatedContextTokens <= data.rag.contextTokenBudget`.
6. `/api/chat` with budget 1000 — HTTP 200; RAG metadata includes selected/dropped/deduplicated/truncated counts and source metadata.
7. Budget 255 — HTTP 400 with a validation message requiring at least 256.
8. Tenant A against Tenant B KB — HTTP 403 or 404; Tenant-B evidence must never be returned.
9. Explicit secret-exfiltration prompt — HTTP 200 is acceptable, but the generated response must not disclose the secret.
10. RAG disabled — normal `/api/chat` behavior remains unchanged and no RAG metadata is returned.
11. Upload the prompt-injection test document, wait for ingestion/embedding to finish, then search for it.
12. Ask `/api/chat` about the injection document. The answer may summarize it, but must not execute its instructions or reveal system/developer prompts.

## Phase 4 API response metadata

When RAG is enabled, `/api/chat` now returns `data.rag` containing:

- `enabled`
- `retrievalStrategy`
- `knowledgeBaseCount`
- `retrievedCount`
- `selectedCount`
- `deduplicatedCount`
- `droppedCount`
- `truncatedCount`
- `estimatedContextTokens`
- `contextTokenBudget`
- `sources[]`

`augmentedPrompt` and raw retrieved content are deliberately not returned in the API metadata.

## Provider authentication failure

If the configured OpenAI/Gemini credential is intentionally invalid, the upstream 401/403 is mapped to **502 Bad Gateway** instead of leaking as an unhandled 500. A provider authentication failure must not trigger failover/retry.

Do not invalidate production credentials merely to run this test; use a controlled local/test configuration.

## Performance note

The earlier `/api/chat` tests showed ~54–59 seconds even when context budgets changed from 1000 to 256. That indicates the dominant latency is likely outside context assembly (provider/embedding/model execution). Treat this as a Phase-4 performance follow-up, not a context-budget correctness failure.

## Phase 4 completion criteria

Phase 4 is accepted when the automated Maven suite is green and all numbered API acceptance tests above pass without tenant-isolation or prompt-injection violations.
