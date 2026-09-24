package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;

import java.util.UUID;

/**
 * Admission state created before a streaming HTTP response is committed.
 * Security failures must happen before StreamingResponseBody starts so that
 * Spring can still return the correct HTTP status (403/503).
 */
public record StreamAdmission(
        UUID requestId,
        UUID inferenceId,
        AuthenticationContext authenticationContext,
        long startedAtNanos) {
}
