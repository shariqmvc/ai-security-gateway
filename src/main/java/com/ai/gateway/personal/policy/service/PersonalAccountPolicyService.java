package com.ai.gateway.personal.policy.service;

import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.policy.entity.PersonalAccountPolicy;
import com.ai.gateway.personal.policy.repository.PersonalAccountFeatureRepository;
import com.ai.gateway.personal.policy.repository.PersonalAccountFreeModelRepository;
import com.ai.gateway.personal.policy.repository.PersonalAccountPolicyRepository;
import com.ai.gateway.personal.quota.config.PersonalQuotaProperties;
import com.ai.gateway.personal.billing.PersonalBillingProperties;
import com.ai.gateway.entitlement.enums.Feature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalAccountPolicyService {

    private final PersonalAccountPolicyRepository policyRepository;
    private final PersonalAccountFeatureRepository featureRepository;
    private final PersonalAccountFreeModelRepository freeModelRepository;
    private final PersonalQuotaProperties quotaDefaults;
    private final PersonalBillingProperties billingDefaults;

    @Transactional
    public PersonalAccountPolicy ensurePolicy(PersonalAccount account) {
        return policyRepository.findByPersonalAccountId(account.getId())
                .orElseGet(() -> {
                    PersonalAccountPolicy policy = policyRepository.save(
                            PersonalAccountPolicy.builder()
                                    .personalAccount(account)
                                    .quotaEnabled(quotaDefaults.isEnabled())
                                    .requestsPerMinute(quotaDefaults.getRequestsPerMinute())
                                    .requestsPerDay(quotaDefaults.getRequestsPerDay())
                                    .monthlyTokenQuota(quotaDefaults.getMonthlyTokenQuota())
                                    .maxInputTokens(quotaDefaults.getMaxInputTokens())
                                    .maxOutputTokens(quotaDefaults.getMaxOutputTokens())
                                    .maxConcurrentRequests(quotaDefaults.getMaxConcurrentRequests())
                                    .updatedAt(LocalDateTime.now())
                                    .build());

                    for (Feature feature : defaultFeatures()) {
                        featureRepository.save(PersonalAccountFeature.builder()
                                .personalAccount(account)
                                .feature(feature.name())
                                .build());
                    }

                    for (String model : billingDefaults.getFreeModels()) {
                        freeModelRepository.save(PersonalAccountFreeModel.builder()
                                .personalAccount(account)
                                .modelKey(model)
                                .build());
                    }

                    return policy;
                });
    }

    @Transactional(readOnly = true)
    public PersonalAccountPolicy get(UUID accountId) {
        return policyRepository.findByPersonalAccountId(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "Personal account policy is not configured."));
    }

    @Transactional(readOnly = true)
    public Set<Feature> enabledFeatures(UUID accountId) {
        return featureRepository.findAllByPersonalAccountId(accountId).stream()
                .map(PersonalAccountFeature::getFeature)
                .map(Feature::valueOf)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<String> freeModels(UUID accountId) {
        return freeModelRepository.findAllByPersonalAccountId(accountId).stream()
                .map(PersonalAccountFreeModel::getModelKey)
                .collect(Collectors.toSet());
    }

    private Set<Feature> defaultFeatures() {
        return Set.of(
                Feature.OPENAI, Feature.GEMINI, Feature.CLAUDE, Feature.XAI, Feature.GROQ,
                Feature.OLLAMA, Feature.CHAT, Feature.STREAMING, Feature.EMBEDDING,
                Feature.PROMPT_FIREWALL, Feature.POLICY_ENGINE, Feature.PII_DETECTION,
                Feature.AUDIT, Feature.METRICS, Feature.TOKEN_ANALYTICS, Feature.COST_ANALYTICS,
                Feature.RATE_LIMITING, Feature.QUOTA, Feature.RAG, Feature.MCP,
                Feature.EXTENSIVE_RESEARCH, Feature.DASHBOARD
        );
    }
}
