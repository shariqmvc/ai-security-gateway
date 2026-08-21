package com.ai.gateway.rag.knowledge;

import com.ai.gateway.exception.ErrorResponse;
import com.ai.gateway.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseNotFoundExceptionTest {

    @Test
    void shouldMapMissingKnowledgeBaseToNotFound() {
        UUID id = UUID.randomUUID();
        KnowledgeBaseNotFoundException exception =
                new KnowledgeBaseNotFoundException(id);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI())
                .thenReturn("/api/knowledge-bases/" + id);

        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleKnowledgeBaseNotFound(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("Knowledge base not found: " + id,
                response.getBody().getMessage());
        assertEquals("/api/knowledge-bases/" + id,
                response.getBody().getPath());
    }
}
