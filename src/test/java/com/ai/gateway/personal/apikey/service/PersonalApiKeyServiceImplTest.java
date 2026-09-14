package com.ai.gateway.personal.apikey.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.personal.apikey.dto.PersonalApiKeyCreateRequest;
import com.ai.gateway.personal.apikey.entity.PersonalApiKey;
import com.ai.gateway.personal.apikey.repository.PersonalApiKeyRepository;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.entity.PersonalUser;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.personal.repository.PersonalUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonalApiKeyServiceImplTest {
    @Test
    void createsKeyAndNeverUsesSessionTokenAsApiKey() {
        var keys = mock(PersonalApiKeyRepository.class);
        var accounts = mock(PersonalAccountRepository.class);
        var users = mock(PersonalUserRepository.class);
        var user = PersonalUser.builder().id(UUID.randomUUID()).status("ACTIVE").build();
        var account = PersonalAccount.builder().id(UUID.randomUUID()).user(user).status("ACTIVE").build();
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(keys.findByKeyHash(any())).thenReturn(Optional.empty());
        when(keys.save(any(PersonalApiKey.class))).thenAnswer(i -> {
            PersonalApiKey k=i.getArgument(0); k.setId(UUID.randomUUID()); return k;
        });

        var service = new PersonalApiKeyServiceImpl(keys, accounts, users);
        var session = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_SESSION)
                .personalPrincipal(true).personalUserId(user.getId()).personalAccountId(account.getId()).build();

        var result = service.create(session, new PersonalApiKeyCreateRequest("cli", Set.of("chat"), null));
        assertTrue(result.apiKey().startsWith("arpk_"));
        assertEquals("cli", result.name());
        assertEquals(Set.of("chat"), result.scopes());
        verify(keys).save(any(PersonalApiKey.class));
    }

    @Test
    void apiKeyAuthenticationBuildsPersonalApiKeyContext() {
        var keys = mock(PersonalApiKeyRepository.class);
        var accounts = mock(PersonalAccountRepository.class);
        var users = mock(PersonalUserRepository.class);
        var user = PersonalUser.builder().id(UUID.randomUUID()).status("ACTIVE").build();
        var account = PersonalAccount.builder().id(UUID.randomUUID()).user(user).status("ACTIVE").build();
        var key = PersonalApiKey.builder().id(UUID.randomUUID()).personalAccount(account)
                .name("cli").keyHash("unused").keyPrefix("arpk_test")
                .scopes("chat,models").build();
        when(keys.findByKeyHash(any())).thenReturn(Optional.of(key));

        var service = new PersonalApiKeyServiceImpl(keys, accounts, users);
        var context = service.authenticate("arpk_abcdefghijklmnopqrstuvwxyz");
        assertEquals(AuthenticationType.PERSONAL_API_KEY, context.getAuthenticationType());
        assertTrue(context.isPersonalPrincipal());
        assertEquals(account.getId(), context.getPersonalAccountId());
        assertTrue(service.hasScope(context, "chat"));
        assertTrue(service.hasScope(context, "models"));
    }

    @Test
    void sessionOnlyOperationsRejectApiKeyContext() {
        var service = new PersonalApiKeyServiceImpl(mock(PersonalApiKeyRepository.class),
                mock(PersonalAccountRepository.class), mock(PersonalUserRepository.class));
        var api = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_API_KEY)
                .personalPrincipal(true).personalAccountId(UUID.randomUUID()).build();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.list(api));
    }
}
