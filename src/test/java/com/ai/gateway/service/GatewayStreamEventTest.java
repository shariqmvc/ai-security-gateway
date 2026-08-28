package com.ai.gateway.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GatewayStreamEventTest {

    @Test
    void buildsDeltaEventWithRequestCorrelation() {
        UUID requestId = UUID.randomUUID();

        GatewayStreamEvent event = GatewayStreamEvent.builder()
                .requestId(requestId)
                .type("delta")
                .content("hello")
                .build();

        assertEquals(requestId, event.getRequestId());
        assertEquals("delta", event.getType());
        assertEquals("hello", event.getContent());
        assertNull(event.getError());
    }
}
