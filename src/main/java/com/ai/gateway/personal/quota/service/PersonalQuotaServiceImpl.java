package com.ai.gateway.personal.quota.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.*;
import com.ai.gateway.personal.policy.entity.PersonalAccountPolicy;
import com.ai.gateway.personal.policy.service.PersonalAccountPolicyService;
import com.ai.gateway.personal.quota.exception.PersonalQuotaExceededException;
import com.ai.gateway.personal.quota.repository.PersonalQuotaUsageRepository;
import com.ai.gateway.personal.billing.PersonalBillingProperties;
import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.dto.PreRequestCostRequest;
import com.ai.gateway.core.cost.service.PreRequestCostEstimator;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
public class PersonalQuotaServiceImpl implements PersonalQuotaService {

    private final PersonalAccountPolicyService policyService;
    private final PersonalQuotaUsageRepository repository;
    private final PersonalBillingProperties billingProperties;
    private final PreRequestCostEstimator costEstimator;
    private final ConcurrentHashMap<UUID, AtomicInteger> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<Long>> starts = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void beforeRequest(AuthenticationContext auth, long estimatedInputTokens) {
        if (!personal(auth)) return;

        PersonalAccountPolicy policy = policyService.getOrCreate(auth.getPersonalAccountId());
        if (!policy.isQuotaEnabled()) return;

        UUID id = auth.getPersonalAccountId();

        if (policy.getMaxInputTokens() > 0 && estimatedInputTokens > policy.getMaxInputTokens()) {
            throw new PersonalQuotaExceededException("Personal input token limit exceeded.");
        }

        long maxConcurrent = policy.getMaxConcurrentRequests();
        AtomicInteger counter = active.computeIfAbsent(id, k -> new AtomicInteger());

        if (maxConcurrent > 0) {
            while (true) {
                int current = counter.get();
                if (current >= maxConcurrent) {
                    throw new PersonalQuotaExceededException("Personal concurrent request limit exceeded.");
                }
                if (counter.compareAndSet(current, current + 1)) break;
            }
        } else {
            counter.incrementAndGet();
        }

        try {
            if (policy.getRequestsPerMinute() > 0) {
                long now = System.currentTimeMillis();
                long cutoff = now - 60_000L;
                var q = starts.computeIfAbsent(id, k -> new ConcurrentLinkedDeque<>());
                while (true) {
                    Long t = q.peekFirst();
                    if (t == null || t >= cutoff) break;
                    q.pollFirst();
                }
                synchronized (q) {
                    Long timestamp;
                    while ((timestamp = q.peekFirst()) != null && timestamp < cutoff) q.pollFirst();
                    if (q.size() >= policy.getRequestsPerMinute()) {
                        throw new PersonalQuotaExceededException("Personal requests-per-minute limit exceeded.");
                    }
                    q.addLast(now);
                }
            }

            if (policy.getRequestsPerDay() > 0) {
                int updated = repository.consumeDailyRequest(
                        id, LocalDate.now(), policy.getRequestsPerDay());
                if (updated == 0) {
                    throw new PersonalQuotaExceededException("Personal daily request quota exceeded.");
                }
            }
        } catch (RuntimeException e) {
            release(auth);
            throw e;
        }
    }

    @Override
    @Transactional
    public void afterSuccess(AuthenticationContext auth, AIResponse response) {
        if (!personal(auth)) return;

        PersonalAccountPolicy policy = policyService.getOrCreate(auth.getPersonalAccountId());
        if (!policy.isQuotaEnabled()) return;

        var u = response == null ? null : response.getUsage();
        long input = u == null || u.getInputTokens() == null ? 0 : u.getInputTokens();
        long output = u == null || u.getOutputTokens() == null ? 0 : u.getOutputTokens();
        long total = u == null || u.getTotalTokens() == null ? input + output : u.getTotalTokens();

        if (policy.getMaxOutputTokens() > 0 && output > policy.getMaxOutputTokens()) {
            throw new PersonalQuotaExceededException("Personal output token limit exceeded.");
        }

        if (policy.getMonthlyTokenQuota() > 0 && total > 0) {
            int updated = repository.consumeMonthlyTokens(
                    auth.getPersonalAccountId(),
                    YearMonth.now().atDay(1),
                    total,
                    policy.getMonthlyTokenQuota());
            if (updated == 0) {
                throw new PersonalQuotaExceededException("Personal monthly token quota exceeded.");
            }
        }
    }


    @Override
    @Transactional
    public void enforceAndRecordFreeComputeCost(AuthenticationContext auth, AIRequest request, AIResponse response) {
        if (!personal(auth) || request == null || response == null || response.getProvider() == null || response.getModel() == null) return;
        if (!"FREE".equalsIgnoreCase(request.getBillingMode())) return;

        var usage = response.getUsage();
        int input = usage == null || usage.getInputTokens() == null ? 0 : usage.getInputTokens();
        int output = usage == null || usage.getOutputTokens() == null ? 0 : usage.getOutputTokens();

        PreRequestCostEstimate estimate = costEstimator.estimate(
                PreRequestCostRequest.builder()
                        .provider(response.getProvider())
                        .model(response.getModel())
                        .inputTokens(input)
                        .outputTokens(output)
                        .cachedInputTokens(0)
                        .build());

        BigDecimal cost = estimate.getTotalEstimatedCost() == null ? BigDecimal.ZERO : estimate.getTotalEstimatedCost();
        BigDecimal cap = billingProperties.getMonthlyFreeComputeCreditCap();
        if (cap == null || cap.compareTo(BigDecimal.ZERO) < 0) cap = BigDecimal.ZERO;

        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            int updated = repository.consumeMonthlyFreeComputeCost(
                    auth.getPersonalAccountId(), YearMonth.now().atDay(1), cost, cap);
            if (updated == 0 && cap.compareTo(BigDecimal.ZERO) > 0) {
                throw new PersonalQuotaExceededException(
                        "Personal monthly free-model compute cap exceeded.");
            }
        }
    }

    @Override
    public void release(AuthenticationContext auth) {
        if (!personal(auth)) return;
        AtomicInteger c = active.get(auth.getPersonalAccountId());
        if (c != null && c.decrementAndGet() <= 0) {
            active.remove(auth.getPersonalAccountId(), c);
        }
    }

    @Override
    public long activeRequests(UUID id) {
        AtomicInteger c = active.get(id);
        return c == null ? 0 : c.get();
    }

    private boolean personal(AuthenticationContext a) {
        return a != null && a.isPersonalPrincipal() && a.getPersonalAccountId() != null;
    }
}
