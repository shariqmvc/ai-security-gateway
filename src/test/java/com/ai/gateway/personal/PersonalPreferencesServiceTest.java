package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalPreferencesResponse;
import com.ai.gateway.personal.dto.PersonalPreferencesUpdateRequest;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.preferences.entity.PersonalAccountPreferences;
import com.ai.gateway.personal.preferences.repository.PersonalAccountPreferencesRepository;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalPreferencesServiceTest {

    @Mock
    private PersonalAccountRepository accountRepository;

    @Mock
    private PersonalAccountPreferencesRepository preferencesRepository;

    @InjectMocks
    private PersonalPreferencesService preferencesService;

    @Test
    void updatesOnlyUserPreferences() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .status("ACTIVE")
                .plan("PERSONAL_FREE")
                .build();

        PersonalAccountPreferences preferences = PersonalAccountPreferences.builder()
                .personalAccount(account)
                .billingMode("AUTO")
                .routingPriority("BALANCED")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId)).thenReturn(Optional.of(preferences));
        when(preferencesRepository.save(any(PersonalAccountPreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        PersonalPreferencesResponse result = preferencesService.update(
                context,
                new PersonalPreferencesUpdateRequest(
                        "ollama",
                        "llama3.2:3b",
                        "byok",
                        "latency"));

        assertEquals("OLLAMA", result.defaultProvider());
        assertEquals("llama3.2:3b", result.defaultModel());
        assertEquals("BYOK", result.billingMode());
        assertEquals("LATENCY", result.routingPriority());
        verify(preferencesRepository).save(preferences);
    }

    @Test
    void rejectsUnsupportedBillingMode() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .status("ACTIVE")
                .plan("PERSONAL_FREE")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId))
                .thenReturn(Optional.of(PersonalAccountPreferences.builder()
                        .personalAccount(account)
                        .billingMode("AUTO")
                        .routingPriority("BALANCED")
                        .build()));

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> preferencesService.update(
                        context,
                        new PersonalPreferencesUpdateRequest(
                                null, null, "INVALID", null)));
    }
}
