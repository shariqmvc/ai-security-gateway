package com.ai.gateway.rag.document;

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

class DocumentNotFoundExceptionTest {

    @Test
    void shouldMapMissingDocumentToNotFound() {
        UUID id = UUID.randomUUID();
        DocumentNotFoundException exception = new DocumentNotFoundException(id);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/knowledge-bases/kb/documents/" + id);

        ResponseEntity<ErrorResponse> response =
                new GlobalExceptionHandler().handleDocumentNotFound(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Document not found: " + id, response.getBody().getMessage());
    }
}
