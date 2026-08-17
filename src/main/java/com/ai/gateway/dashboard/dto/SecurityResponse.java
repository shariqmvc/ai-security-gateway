package com.ai.gateway.dashboard.dto;

public record SecurityResponse(
        String scope,
        long auditEvents,
        long successfulEvents,
        long failedEvents) {
}
