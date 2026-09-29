package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalEffectiveSettingsResponse;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.policy.entity.PersonalAccountPolicy;
import com.ai.gateway.personal.policy.service.PersonalAccountPolicyService;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalSettingsService {

    private final PersonalAccountRepository accountRepository;
    private final PersonalAccountPolicyService policyService;

    @Transactional
    public PersonalEffectiveSettingsResponse getEffectiveSettings(AuthenticationContext context) {
        if (context == null
                || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new AccessDeniedException("Personal authentication is required.");
        }

        PersonalAccount account = accountRepository.findById(context.getPersonalAccountId())
                .orElseThrow(() -> new AccessDeniedException("Personal account not found."));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new AccessDeniedException("Personal account is not active.");
        }

        PersonalAccountPolicy policy = policyService.getOrCreate(account.getId());

        return new PersonalEffectiveSettingsResponse(
                new PersonalEffectiveSettingsResponse.Account(
                        account.getPlan(),
                        account.getStatus()),
                new PersonalEffectiveSettingsResponse.Limits(
                        policy.isQuotaEnabled(),
                        policy.getRequestsPerMinute(),
                        policy.getRequestsPerDay(),
                        policy.getMonthlyTokenQuota(),
                        policy.getMaxInputTokens(),
                        policy.getMaxOutputTokens(),
                        policy.getMaxConcurrentRequests()),
                policyService.enabledFeatures(account.getId()).stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()),
                policyService.freeModels(account.getId()));
    }
}
