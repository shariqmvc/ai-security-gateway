package com.ai.gateway.personal.quota.entity;

import com.ai.gateway.enums.QuotaPeriodType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalQuotaUsageTest {

    @Test
    void builderCreatesAccountScopedUsageWithZeroCounters() {
        UUID accountId = UUID.randomUUID();

        PersonalQuotaUsage usage = PersonalQuotaUsage.builder()
                .personalAccountId(accountId)
                .periodType(QuotaPeriodType.DAILY)
                .periodStart(LocalDate.of(2026, 8, 29))
                .build();

        assertEquals(accountId, usage.getPersonalAccountId());
        assertEquals(QuotaPeriodType.DAILY, usage.getPeriodType());
        assertEquals(LocalDate.of(2026, 8, 29), usage.getPeriodStart());
        assertEquals(0L, usage.getRequestCount());
        assertEquals(0L, usage.getTokenCount());
    }
}
