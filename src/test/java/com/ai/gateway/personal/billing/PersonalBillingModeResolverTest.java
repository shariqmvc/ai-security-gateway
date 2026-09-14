package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.entity.PersonalProviderConnection;
import com.ai.gateway.personal.repository.PersonalProviderConnectionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalBillingModeResolverTest {

    @Test
    void autoUsesByokWhenActiveConnectionExists() {
        var repository = mock(PersonalProviderConnectionRepository.class);
        var properties = new PersonalBillingProperties();
        var accountId = UUID.randomUUID();
        when(repository.findByPersonalAccountIdAndProvider(accountId, Provider.OPENAI))
                .thenReturn(Optional.of(activeConnection()));

        var resolver = new PersonalBillingModeResolver(repository, properties);

        assertEquals(PersonalBillingMode.BYOK,
                resolver.resolve(personalContext(accountId), Provider.OPENAI, "gpt-5", "AUTO"));
    }

    @Test
    void autoFallsBackToCreditWithoutByokConnection() {
        var repository = mock(PersonalProviderConnectionRepository.class);
        var properties = new PersonalBillingProperties();
        var accountId = UUID.randomUUID();
        when(repository.findByPersonalAccountIdAndProvider(accountId, Provider.GEMINI))
                .thenReturn(Optional.empty());

        var resolver = new PersonalBillingModeResolver(repository, properties);

        assertEquals(PersonalBillingMode.CREDIT,
                resolver.resolve(personalContext(accountId), Provider.GEMINI, "gemini-3.6-flash", "AUTO"));
    }

    @Test
    void explicitByokRequiresActiveConnection() {
        var repository = mock(PersonalProviderConnectionRepository.class);
        var properties = new PersonalBillingProperties();
        var accountId = UUID.randomUUID();
        when(repository.findByPersonalAccountIdAndProvider(accountId, Provider.OPENAI))
                .thenReturn(Optional.of(PersonalProviderConnection.builder().status("REVOKED").build()));

        var resolver = new PersonalBillingModeResolver(repository, properties);

        assertThrows(PersonalBillingModeException.class,
                () -> resolver.resolve(personalContext(accountId), Provider.OPENAI, "gpt-5", "BYOK"));
    }

    @Test
    void freeModeNeverSilentlyTreatsARegularModelAsFree() {
        var repository = mock(PersonalProviderConnectionRepository.class);
        var properties = new PersonalBillingProperties();
        var accountId = UUID.randomUUID();

        var resolver = new PersonalBillingModeResolver(repository, properties);

        assertThrows(PersonalBillingModeException.class,
                () -> resolver.resolve(personalContext(accountId), Provider.OPENAI, "gpt-5", "FREE"));
    }

    @Test
    void rejectsNonPersonalAuthentication() {
        var repository = mock(PersonalProviderConnectionRepository.class);
        var properties = new PersonalBillingProperties();
        var resolver = new PersonalBillingModeResolver(repository, properties);

        assertThrows(PersonalBillingModeException.class,
                () -> resolver.resolve(AuthenticationContext.builder().build(),
                        Provider.OPENAI, "gpt-5", "AUTO"));
    }

    private static AuthenticationContext personalContext(UUID accountId) {
        return AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalUserId(UUID.randomUUID())
                .personalAccountId(accountId)
                .build();
    }

    private static PersonalProviderConnection activeConnection() {
        return PersonalProviderConnection.builder().status("ACTIVE").build();
    }
}
