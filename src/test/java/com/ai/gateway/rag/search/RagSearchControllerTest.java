package com.ai.gateway.rag.search;

import com.ai.gateway.authentication.AuthenticationFilter;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import com.ai.gateway.exception.GlobalExceptionHandler;
import com.ai.gateway.rag.knowledge.KnowledgeBaseNotFoundException;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RagSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagSearchService searchService;

    @MockitoBean
    private AuthenticationFilter authenticationFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @MockitoBean
    private TenantAccessGuard tenantAccessGuard;

    @Test
    void shouldSearchKnowledgeBase() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        when(tenantAccessGuard.requireAuthenticatedTenant()).thenReturn(tenantId);
        when(searchService.search(eq(tenantId), eq(knowledgeBaseId), any()))
                .thenReturn(RagSearchResponse.builder()
                        .knowledgeBaseId(knowledgeBaseId)
                        .query("what is pgvector?")
                        .embeddingProvider("OLLAMA")
                        .embeddingModel("nomic-embed-text")
                        .queryEmbeddingDimension(768)
                        .topK(5)
                        .results(List.of())
                        .build());

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "what is pgvector?",
                                  "topK": 5,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.knowledgeBaseId").value(knowledgeBaseId.toString()))
                .andExpect(jsonPath("$.query").value("what is pgvector?"))
                .andExpect(jsonPath("$.embeddingProvider").value("OLLAMA"))
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"))
                .andExpect(jsonPath("$.queryEmbeddingDimension").value(768))
                .andExpect(jsonPath("$.topK").value(5))
                .andExpect(jsonPath("$.results").isArray());

        verify(tenantAccessGuard).requireAuthenticatedTenant();
        verify(searchService).search(eq(tenantId), eq(knowledgeBaseId), any());
    }

    @Test
    void shouldRejectBlankQuery() throws Exception {
        UUID knowledgeBaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "   ",
                                  "topK": 5,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Search query is required."));

        verifyNoInteractions(searchService, tenantAccessGuard);
    }

    @Test
    void shouldRejectTopKAbove100() throws Exception {
        UUID knowledgeBaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "test",
                                  "topK": 101,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("topK must not exceed 100."));

        verifyNoInteractions(searchService, tenantAccessGuard);
    }

    @Test
    void shouldRejectMinScoreAboveOne() throws Exception {
        UUID knowledgeBaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "test",
                                  "topK": 5,
                                  "minScore": 1.01
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("minScore must not exceed 1.0."));

        verifyNoInteractions(searchService, tenantAccessGuard);
    }

    @Test
    void shouldRejectTopKBelowOne() throws Exception {
        UUID knowledgeBaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "test",
                                  "topK": 0,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("topK must be at least 1."));

        verifyNoInteractions(searchService, tenantAccessGuard);
    }

    @Test
    void shouldRejectMissingQuery() throws Exception {
        UUID knowledgeBaseId = UUID.randomUUID();

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topK": 5,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value("Search query is required."));

        verifyNoInteractions(searchService, tenantAccessGuard);
    }

    @Test
    void shouldReturn404WhenKnowledgeBaseIsOutsideTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        when(tenantAccessGuard.requireAuthenticatedTenant()).thenReturn(tenantId);
        when(searchService.search(eq(tenantId), eq(knowledgeBaseId), any()))
                .thenThrow(new KnowledgeBaseNotFoundException(knowledgeBaseId));

        mockMvc.perform(post("/api/knowledge-bases/{knowledgeBaseId}/search", knowledgeBaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "What does the tenant isolation secret document contain?",
                                  "topK": 10,
                                  "minScore": 0.0
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Knowledge base not found: " + knowledgeBaseId));
    }

}
