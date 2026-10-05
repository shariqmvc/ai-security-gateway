package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalPreferencesResponse;
import com.ai.gateway.personal.dto.PersonalPreferencesUpdateRequest;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import com.ai.gateway.personal.policy.service.PersonalAccountPolicyService;
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

    @Mock
    private PersonalAccountPolicyService policyService;

    @Mock
    private ModelRegistry modelRegistry;

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
        when(modelRegistry.find(Provider.OLLAMA, "llama3.2:3b"))
                .thenReturn(Optional.of(new ModelDefinition(
                        Provider.OLLAMA,
                        "llama3.2:3b",
                        "Llama 3.2 3B",
                        com.ai.gateway.core.routing.registry.ModelStatus.ENABLED,
                        java.util.Set.of())));

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

    @Test
    void appliesSavedDefaultsOnlyToOmittedRequestFields() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .status("ACTIVE")
                .plan("PERSONAL_FREE")
                .build();
        PersonalAccountPreferences preferences = PersonalAccountPreferences.builder()
                .personalAccount(account)
                .defaultProvider("OLLAMA")
                .defaultModel("llama3.2:3b")
                .billingMode("BYOK")
                .routingPriority("LATENCY")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId)).thenReturn(Optional.of(preferences));

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        ChatRequest request = ChatRequest.builder()
                .prompt("hello")
                .build();

        preferencesService.applyRequestDefaults(context, request);

        assertEquals(Provider.OLLAMA, request.getProvider());
        assertEquals("llama3.2:3b", request.getModel());
        assertEquals("BYOK", request.getBillingMode());
        assertEquals("LATENCY", request.getRoutingPriority());
    }

    @Test
    void preservesExplicitRequestValuesOverSavedDefaults() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .status("ACTIVE")
                .plan("PERSONAL_FREE")
                .build();
        PersonalAccountPreferences preferences = PersonalAccountPreferences.builder()
                .personalAccount(account)
                .defaultProvider("OLLAMA")
                .defaultModel("llama3.2:3b")
                .billingMode("BYOK")
                .routingPriority("LATENCY")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId)).thenReturn(Optional.of(preferences));

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        ChatRequest request = ChatRequest.builder()
                .prompt("hello")
                .provider(Provider.GEMINI)
                .model("gemini-3.6-flash")
                .billingMode("CREDIT")
                .routingPriority("COST")
                .build();

        preferencesService.applyRequestDefaults(context, request);

        assertEquals(Provider.GEMINI, request.getProvider());
        assertEquals("gemini-3.6-flash", request.getModel());
        assertEquals("CREDIT", request.getBillingMode());
        assertEquals("COST", request.getRoutingPriority());
    }

    @Test
    void preservesAutoRoutingIntentOverSavedDefaults() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = activeAccount(accountId);
        PersonalAccountPreferences preferences = PersonalAccountPreferences.builder()
                .personalAccount(account)
                .defaultProvider("OLLAMA")
                .defaultModel("llama3.2:3b")
                .billingMode("BYOK")
                .routingPriority("BALANCED")
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId)).thenReturn(Optional.of(preferences));

        AuthenticationContext context = personalContext(accountId);

        ChatRequest request = ChatRequest.builder()
                .prompt("hello")
                .model("auto")
                .build();

        preferencesService.applyRequestDefaults(context, request);

        assertNull(request.getProvider());
        assertEquals("auto", request.getModel());
        assertEquals("BYOK", request.getBillingMode());
        assertEquals("BALANCED", request.getRoutingPriority());
    }

    @Test
    void rejectsUnknownDefaultModel() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = activeAccount(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId))
                .thenReturn(Optional.of(existingPreferences(account)));
        when(modelRegistry.find(Provider.OLLAMA, "missing"))
                .thenReturn(Optional.empty());

        AuthenticationContext context = personalContext(accountId);

        assertThrows(IllegalArgumentException.class, () -> preferencesService.update(
                context,
                new PersonalPreferencesUpdateRequest(
                        "OLLAMA", "missing", "AUTO", null)));
        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void rejectsFreeDefaultModelNotInPolicy() {
        UUID accountId = UUID.randomUUID();
        PersonalAccount account = activeAccount(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(preferencesRepository.findByPersonalAccountId(accountId))
                .thenReturn(Optional.of(existingPreferences(account)));
        ModelDefinition model = mock(ModelDefinition.class);
        when(model.provider()).thenReturn(Provider.OLLAMA);
        when(model.modelId()).thenReturn("llama3.2:3b");
        when(model.isEnabled()).thenReturn(true);
        when(modelRegistry.find(Provider.OLLAMA, "llama3.2:3b"))
                .thenReturn(Optional.of(model));
        when(policyService.freeModels(accountId)).thenReturn(java.util.Set.of());

        assertThrows(IllegalArgumentException.class, () -> preferencesService.update(
                personalContext(accountId),
                new PersonalPreferencesUpdateRequest(
                        "OLLAMA", "llama3.2:3b", "FREE", null)));
        verify(preferencesRepository, never()).save(any());
    }

    private PersonalAccount activeAccount(UUID accountId) {
        return PersonalAccount.builder().id(accountId).status("ACTIVE").plan("PERSONAL_FREE").build();
    }

    private PersonalAccountPreferences existingPreferences(PersonalAccount account) {
        return PersonalAccountPreferences.builder()
                .personalAccount(account)
                .billingMode("AUTO")
                .routingPriority("BALANCED")
                .build();
    }

    private AuthenticationContext personalContext(UUID accountId) {
        return AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();
    }
}
