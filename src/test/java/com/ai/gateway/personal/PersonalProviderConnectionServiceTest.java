package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.dto.PersonalProviderConnectionResponse;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.util.EncryptionUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalProviderConnectionServiceTest {

    @Test
    void shouldListOnlyConnectionsForAuthenticatedPersonalAccount() {
        UUID accountId = UUID.randomUUID();
        PersonalProviderConnectionRepository repository =
                mock(PersonalProviderConnectionRepository.class);
        PersonalAccountRepository accountRepository =
                mock(PersonalAccountRepository.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);

        PersonalProviderConnection connection =
                PersonalProviderConnection.builder()
                        .id(UUID.randomUUID())
                        .provider(Provider.GEMINI)
                        .displayName("My Gemini")
                        .encryptedApiKey("encrypted")
                        .status("ACTIVE")
                        .build();

        when(repository.findAllByPersonalAccountIdOrderByProviderAsc(accountId))
                .thenReturn(List.of(connection));

        PersonalProviderConnectionServiceImpl service =
                new PersonalProviderConnectionServiceImpl(
                        accountRepository,
                        repository,
                        encryptionUtil);

        // System-level endpoint configuration fields are irrelevant to list().
        setField(service, "openAiBaseUrl", "https://api.openai.com");
        setField(service, "geminiBaseUrl", "https://generativelanguage.googleapis.com");
        setField(service, "anthropicBaseUrl", "https://api.anthropic.com");

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .personalUserId(UUID.randomUUID())
                .build();

        List<PersonalProviderConnectionResponse> result =
                service.list(context);

        assertEquals(1, result.size());
        assertEquals(Provider.GEMINI, result.getFirst().provider());
        assertEquals("Connected", result.getFirst().maskedCredential());

        verify(repository)
                .findAllByPersonalAccountIdOrderByProviderAsc(accountId);
    }

    private static void setField(
            Object target,
            String fieldName,
            String value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
