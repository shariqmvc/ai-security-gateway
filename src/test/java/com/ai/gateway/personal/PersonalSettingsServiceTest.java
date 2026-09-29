package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalEffectiveSettingsResponse;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.policy.entity.PersonalAccountPolicy;
import com.ai.gateway.personal.policy.service.PersonalAccountPolicyService;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalSettingsServiceTest {

    @Mock
    private PersonalAccountRepository accountRepository;

    @Mock
    private PersonalAccountPolicyService policyService;

    @InjectMocks
    private PersonalSettingsService settingsService;

    @Test
    void resolvesEffectiveSettingsFromAccountPolicy() {
        UUID accountId = UUID.randomUUID();

        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .plan("PERSONAL_FREE")
                .status("ACTIVE")
                .build();

        PersonalAccountPolicy policy = PersonalAccountPolicy.builder()
                .personalAccount(account)
                .quotaEnabled(true)
                .requestsPerMinute(10)
                .requestsPerDay(100)
                .monthlyTokenQuota(100000)
                .maxInputTokens(16000)
                .maxOutputTokens(4096)
                .maxConcurrentRequests(1)
                .build();

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(java.util.Optional.of(account));
        when(policyService.getOrCreate(accountId)).thenReturn(policy);
        when(policyService.enabledFeatures(accountId)).thenReturn(
                Set.of(com.ai.gateway.entitlement.enums.Feature.CHAT));
        when(policyService.freeModels(accountId)).thenReturn(
                Set.of("OLLAMA:llama3.2:3b"));

        PersonalEffectiveSettingsResponse result =
                settingsService.getEffectiveSettings(context);

        assertEquals("PERSONAL_FREE", result.account().plan());
        assertEquals("ACTIVE", result.account().status());
        assertTrue(result.limits().quotaEnabled());
        assertEquals(10, result.limits().requestsPerMinute());
        assertEquals(100, result.limits().requestsPerDay());
        assertEquals(100000, result.limits().monthlyTokenQuota());
        assertEquals(16000, result.limits().maxInputTokens());
        assertEquals(4096, result.limits().maxOutputTokens());
        assertEquals(1, result.limits().maxConcurrentRequests());
        assertEquals(Set.of("CHAT"), result.features());
        assertEquals(Set.of("OLLAMA:llama3.2:3b"), result.freeModels());
    }

    @Test
    void rejectsNonPersonalAuthentication() {
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(false)
                .personalAccountId(UUID.randomUUID())
                .build();

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> settingsService.getEffectiveSettings(context));
    }
}
