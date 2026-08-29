package com.ai.gateway.personal;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import com.ai.gateway.util.EncryptionUtil;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalProviderCredentialResolverTest {

    @Test
    void shouldDecryptActivePersonalProviderCredential() {
        UUID accountId = UUID.randomUUID();
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        PersonalProviderConnectionRepository repository =
                mock(PersonalProviderConnectionRepository.class);

        PersonalProviderConnection connection =
                PersonalProviderConnection.builder()
                        .id(UUID.randomUUID())
                        .provider(Provider.OPENAI)
                        .encryptedApiKey("encrypted")
                        .status("ACTIVE")
                        .build();

        when(repository.findByPersonalAccountIdAndProvider(
                accountId, Provider.OPENAI))
                .thenReturn(Optional.of(connection));
        when(encryptionUtil.decrypt("encrypted"))
                .thenReturn("sk-personal");

        PersonalProviderCredentialResolver resolver =
                new PersonalProviderCredentialResolver(
                        repository,
                        encryptionUtil);

        assertEquals(
                "sk-personal",
                resolver.resolveApiKey(accountId, Provider.OPENAI));

        verify(encryptionUtil).decrypt("encrypted");
    }

    @Test
    void shouldRejectInactiveConnection() {
        UUID accountId = UUID.randomUUID();
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        PersonalProviderConnectionRepository repository =
                mock(PersonalProviderConnectionRepository.class);

        PersonalProviderConnection connection =
                PersonalProviderConnection.builder()
                        .provider(Provider.OPENAI)
                        .encryptedApiKey("encrypted")
                        .status("DISCONNECTED")
                        .build();

        when(repository.findByPersonalAccountIdAndProvider(
                accountId, Provider.OPENAI))
                .thenReturn(Optional.of(connection));

        PersonalProviderCredentialResolver resolver =
                new PersonalProviderCredentialResolver(
                        repository,
                        encryptionUtil);

        assertThrows(
                PersonalProviderConnectionException.class,
                () -> resolver.resolveApiKey(accountId, Provider.OPENAI));

        verifyNoInteractions(encryptionUtil);
    }
}
