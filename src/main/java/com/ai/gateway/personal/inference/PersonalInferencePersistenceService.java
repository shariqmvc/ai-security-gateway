package com.ai.gateway.personal.inference;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.personal.intelligence.PersonalSecurityAssessment;
import com.ai.gateway.rag.augmentation.RagAugmentationResult;
import com.ai.gateway.rag.augmentation.RagContextChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account-scoped persistence for Personal inference history and telemetry.
 *
 * PostgreSQL is the source of truth for Personal inference data in v1.
 * Content is expected to be masked/sanitized before it is persisted.
 * Persistence failures are deliberately isolated from inference execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalInferencePersistenceService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public UUID start(
            AuthenticationContext auth,
            UUID requestId,
            String endpoint,
            String operation,
            String prompt) {

        if (!personal(auth)) {
            return null;
        }

        UUID inferenceId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        try {
            jdbc.update("""
                    INSERT INTO PERSONAL_INFERENCE_RUNS
                    (id, request_id, personal_account_id, api_key_id, operation, endpoint,
                     status, started_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?)
                    """,
                    inferenceId, requestId, auth.getPersonalAccountId(),
                    auth.getApiKeyId(), operation, endpoint, now, now, now);

            if (prompt != null) {
                message(inferenceId, "USER", 0, prompt, null);
            }

            event(inferenceId, "REQUEST_RECEIVED", "REQUEST",
                    Map.of("endpoint", endpoint, "operation", operation));

            return inferenceId;
        } catch (Exception ex) {
            log.warn("Unable to start Personal inference persistence requestId={}", requestId, ex);
            return null;
        }
    }

    public void updateTarget(
            UUID inferenceId,
            AIRequest request) {
        if (inferenceId == null || request == null) return;
        execute(() -> jdbc.update("""
                UPDATE PERSONAL_INFERENCE_RUNS
                   SET requested_model=?,
                       selected_model=?,
                       selected_provider=?,
                       billing_mode=?,
                       status='PROCESSING',
                       updated_at=?
                 WHERE id=?
                """,
                request.getModel(),
                request.getModel(),
                request.getProvider() == null ? null : request.getProvider().name(),
                request.getBillingMode(),
                LocalDateTime.now(),
                inferenceId));
    }

    public void addUserPrompt(UUID inferenceId, String prompt) {
        if (inferenceId == null || prompt == null) return;
        execute(() -> message(inferenceId, "USER", 0, prompt, null));
    }

    public void addContext(UUID inferenceId, String optimizedContext, Integer tokenCount) {
        if (inferenceId == null || optimizedContext == null) return;
        execute(() -> message(inferenceId, "CONTEXT", 1, optimizedContext, tokenCount));
    }

    public void addResponse(UUID inferenceId, String response, Integer tokenCount) {
        if (inferenceId == null || response == null) return;
        execute(() -> message(inferenceId, "ASSISTANT", 2, response, tokenCount));
    }

    public void event(
            UUID inferenceId,
            String eventType,
            String stage,
            Map<String, ?> data) {
        if (inferenceId == null) return;
        execute(() -> {
            jdbc.update("""
                    INSERT INTO PERSONAL_INFERENCE_EVENTS
                    (id, inference_id, event_type, event_stage, event_data, occurred_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    """,
                    UUID.randomUUID(), inferenceId, eventType, stage,
                    json(data), LocalDateTime.now());
        });
    }

    public void security(
            UUID inferenceId,
            PersonalSecurityAssessment assessment) {
        if (inferenceId == null || assessment == null) return;
        execute(() -> jdbc.update("""
                INSERT INTO PERSONAL_INFERENCE_SECURITY_EVENTS
                (id, inference_id, check_type, decision, risk_score, categories,
                 detected_items, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                """,
                UUID.randomUUID(), inferenceId,
                assessment.model() != null && assessment.model().startsWith("deberta")
                        ? "PERSONAL_FIREWALL_ML"
                        : "PERSONAL_SECURITY_INTELLIGENCE",
                assessment.shouldBlock() ? "BLOCK" : "ALLOW",
                assessment.score(),
                json(assessment.labels()),
                json(assessment.signals()),
                json(Map.of(
                        "risk", assessment.risk().name(),
                        "decision", assessment.decision(),
                        "model", assessment.model() == null ? "" : assessment.model(),
                        "modelVersion", assessment.modelVersion() == null ? "" : assessment.modelVersion(),
                        "latencyMs", assessment.latencyMs())),
                LocalDateTime.now()));

        event(inferenceId, "FIREWALL_ML_DETECTION", "SECURITY", Map.of(
                "decision", assessment.decision(),
                "risk", assessment.risk().name(),
                "score", assessment.score(),
                "labels", assessment.labels(),
                "model", assessment.model() == null ? "" : assessment.model(),
                "modelVersion", assessment.modelVersion() == null ? "" : assessment.modelVersion(),
                "latencyMs", assessment.latencyMs()));
    }

    public void securityDecision(
            UUID inferenceId,
            PersonalSecurityAssessment assessment) {
        if (inferenceId == null || assessment == null) return;
        event(inferenceId, "SECURITY_DECISION", "SECURITY", Map.of(
                "decision", assessment.decision(),
                "risk", assessment.risk().name(),
                "score", assessment.score(),
                "labels", assessment.labels()));
    }

    public void pii(
            UUID inferenceId,
            List<String> categories,
            int detectedCount,
            boolean masked) {
        if (inferenceId == null) return;
        execute(() -> {
            String decision = detectedCount > 0 ? "REDACT" : "ALLOW";
            Map<String, Object> details = Map.of(
                    "detected", detectedCount > 0,
                    "count", detectedCount,
                    "masked", masked);
            jdbc.update("""
                    INSERT INTO PERSONAL_INFERENCE_SECURITY_EVENTS
                    (id, inference_id, check_type, decision, risk_score, categories,
                     detected_items, details, created_at)
                    VALUES (?, ?, 'PERSONAL_PII', ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                    """,
                    UUID.randomUUID(), inferenceId, decision,
                    detectedCount > 0 ? 20 : 0,
                    json(categories), json(categories), json(details), LocalDateTime.now());

            event(inferenceId, "PII_DETECTION", "SECURITY", Map.of(
                    "detected", detectedCount > 0,
                    "count", detectedCount,
                    "categories", categories == null ? List.of() : categories));
        });
    }

    public void retrievals(
            UUID inferenceId,
            RagAugmentationResult result) {
        if (inferenceId == null || result == null || result.getChunks() == null) return;

        int rank = 1;
        for (RagContextChunk chunk : result.getChunks()) {
            final int currentRank = rank++;
            execute(() -> jdbc.update("""
                    INSERT INTO PERSONAL_INFERENCE_RETRIEVALS
                    (id, inference_id, knowledge_base_id, document_id, chunk_id,
                     rank, similarity_score, retrieved_content, metadata, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """,
                    UUID.randomUUID(), inferenceId, chunk.getKnowledgeBaseId(),
                    chunk.getDocumentId(),
                    chunk.getChunkId() == null ? chunk.getId() == null ? null : chunk.getId().toString() : chunk.getChunkId(),
                    currentRank, chunk.getSimilarity(), chunk.getContent(),
                    json(chunk.getMetadataJson() == null ? Map.of() : Map.of("source", chunk.getMetadataJson())),
                    LocalDateTime.now()));
        }
    }

    public void providerAttempt(
            UUID inferenceId,
            int attempt,
            String provider,
            String model,
            String status,
            long startedNanos,
            long completedNanos,
            AIResponse response,
            Throwable error) {
        if (inferenceId == null) return;

        Integer in = response != null && response.getUsage() != null ? response.getUsage().getInputTokens() : null;
        Integer out = response != null && response.getUsage() != null ? response.getUsage().getOutputTokens() : null;
        Integer total = response != null && response.getUsage() != null ? response.getUsage().getTotalTokens() : null;
        long duration = Math.max(0L, (completedNanos - startedNanos) / 1_000_000L);

        execute(() -> jdbc.update("""
                INSERT INTO PERSONAL_PROVIDER_ATTEMPTS
                (id, inference_id, attempt_no, provider, model, status,
                 request_started_at, response_received_at, duration_ms,
                 input_tokens, output_tokens, total_tokens, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), inferenceId, attempt, provider, model, status,
                LocalDateTime.now().minusNanos(Math.max(0L, completedNanos - startedNanos)),
                LocalDateTime.now(), duration, in, out, total,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : truncate(error.getMessage(), 2000),
                LocalDateTime.now()));
    }

    public void completeSuccess(
            UUID inferenceId,
            AIRequest request,
            AIResponse response,
            long latencyMs,
            Integer originalTokens,
            Integer optimizedTokens,
            Integer tokensSaved,
            Integer contextWindowTokens,
            boolean cacheHit,
            boolean ragEnabled) {
        if (inferenceId == null) return;

        execute(() -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("""
                    UPDATE PERSONAL_INFERENCE_RUNS
                       SET status='SUCCESS',
                           selected_model=?,
                           selected_provider=?,
                           billing_mode=?,
                           completed_at=?,
                           duration_ms=?,
                           updated_at=?
                     WHERE id=?
                    """,
                    request == null ? null : request.getModel(),
                    request == null || request.getProvider() == null ? null : request.getProvider().name(),
                    request == null ? null : request.getBillingMode(),
                    now, latencyMs, now, inferenceId);

            if (response != null) {
                addResponse(inferenceId, response.getResponse(),
                        response.getUsage() == null ? null : response.getUsage().getOutputTokens());

                Integer in = response.getUsage() == null ? null : response.getUsage().getInputTokens();
                Integer out = response.getUsage() == null ? null : response.getUsage().getOutputTokens();
                Integer total = response.getUsage() == null ? null : response.getUsage().getTotalTokens();

                jdbc.update("""
                        INSERT INTO PERSONAL_INFERENCE_USAGE
                        (id, inference_id, input_tokens, output_tokens, total_tokens,
                         original_input_tokens, optimized_input_tokens, tokens_saved,
                         optimization_ratio, estimated_cost, actual_cost, currency, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USD', ?)
                        """,
                        UUID.randomUUID(), inferenceId, in, out, total,
                        originalTokens, optimizedTokens, tokensSaved,
                        optimizationRatio(originalTokens, optimizedTokens),
                        null, null, now);
            }

            event(inferenceId, "REQUEST_COMPLETED", "REQUEST",
                    Map.of("status", "SUCCESS", "latencyMs", latencyMs,
                            "cacheHit", cacheHit, "ragEnabled", ragEnabled));
        });
    }

    public void completeBlocked(
            UUID inferenceId,
            AIRequest request,
            long latencyMs,
            String errorCode,
            String errorMessage) {
        if (inferenceId == null) return;

        execute(() -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("""
                    UPDATE PERSONAL_INFERENCE_RUNS
                       SET status='BLOCKED',
                           selected_model=?,
                           selected_provider=?,
                           billing_mode=?,
                           completed_at=?,
                           duration_ms=?,
                           error_code=?,
                           error_message=?,
                           updated_at=?
                     WHERE id=?
                    """,
                    request == null ? null : request.getModel(),
                    request == null || request.getProvider() == null ? null : request.getProvider().name(),
                    request == null ? null : request.getBillingMode(),
                    now, latencyMs, errorCode, truncate(errorMessage, 2000), now, inferenceId);

            event(inferenceId, "REQUEST_BLOCKED", "REQUEST",
                    Map.of("errorCode", errorCode == null ? "SECURITY_BLOCKED" : errorCode,
                            "latencyMs", latencyMs));
        });
    }

    public void completeFailure(
            UUID inferenceId,
            AIRequest request,
            long latencyMs,
            String errorCode,
            String errorMessage) {
        if (inferenceId == null) return;

        execute(() -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("""
                    UPDATE PERSONAL_INFERENCE_RUNS
                       SET status='FAILED',
                           selected_model=?,
                           selected_provider=?,
                           billing_mode=?,
                           completed_at=?,
                           duration_ms=?,
                           error_code=?,
                           error_message=?,
                           updated_at=?
                     WHERE id=?
                    """,
                    request == null ? null : request.getModel(),
                    request == null || request.getProvider() == null ? null : request.getProvider().name(),
                    request == null ? null : request.getBillingMode(),
                    now, latencyMs, errorCode, truncate(errorMessage, 2000), now, inferenceId);

            event(inferenceId, "REQUEST_FAILED", "REQUEST",
                    Map.of("errorCode", errorCode == null ? "UNKNOWN" : errorCode,
                            "latencyMs", latencyMs));
        });
    }

    private void message(UUID inferenceId, String role, int sequence,
                         String content, Integer tokenCount) {
        jdbc.update("""
                INSERT INTO PERSONAL_INFERENCE_MESSAGES
                (id, inference_id, sequence_no, role, content, token_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), inferenceId, sequence, role, content, tokenCount,
                LocalDateTime.now());
    }

    private BigDecimal optimizationRatio(Integer original, Integer optimized) {
        if (original == null || original <= 0 || optimized == null) return null;
        return BigDecimal.valueOf(Math.max(0d, 1d - ((double) optimized / original)));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean personal(AuthenticationContext auth) {
        return auth != null && auth.isPersonalPrincipal() && auth.getPersonalAccountId() != null;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void execute(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Personal inference telemetry persistence failed", ex);
        }
    }
}
