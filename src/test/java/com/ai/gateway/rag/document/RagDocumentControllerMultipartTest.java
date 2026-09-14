package com.ai.gateway.rag.document;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.rag.PersonalRagService;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.rag.embedding.RagDocumentEmbeddingProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class RagDocumentControllerMultipartTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RagDocumentService service;
    @MockitoBean RagDocumentUploadService uploadService;
    @MockitoBean TenantAccessGuard tenantAccessGuard;
    @MockitoBean RagDocumentEmbeddingProcessor embeddingProcessor;
    @MockitoBean PersonalRagService personalRagService;
    @MockitoBean com.ai.gateway.authentication.AuthenticationFilter authenticationFilter;
    @MockitoBean com.ai.gateway.ratelimit.filter.RateLimitFilter rateLimitFilter;
    @MockitoBean com.ai.gateway.personal.ratelimit.filter.PersonalRateLimitFilter personalRateLimitFilter;
    @MockitoBean com.ai.gateway.personal.apikey.filter.PersonalApiKeyScopeFilter personalApiKeyScopeFilter;

    @Test
    void multipartAtCanonicalDocumentsPathUsesUploadHandler() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID kbId = UUID.randomUUID();
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        when(personalRagService.upload(eq(context), eq(kbId), any()))
                .thenReturn(DocumentResponse.builder().id(UUID.randomUUID()).knowledgeBaseId(kbId).build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello rag".getBytes());

        mockMvc.perform(multipart("/api/knowledge-bases/{id}/documents", kbId)
                        .file(file)
                        .requestAttr(AuthenticationConstants.AUTH_CONTEXT, context))
                .andExpect(status().isAccepted());

        verify(personalRagService).upload(eq(context), eq(kbId), any());
    }
}
