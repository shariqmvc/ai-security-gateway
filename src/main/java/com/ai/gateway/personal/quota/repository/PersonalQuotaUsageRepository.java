package com.ai.gateway.personal.quota.repository;

import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.personal.quota.entity.PersonalQuotaUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PersonalQuotaUsageRepository
        extends JpaRepository<PersonalQuotaUsage, UUID> {

    Optional<PersonalQuotaUsage>
    findByPersonalAccountIdAndPeriodTypeAndPeriodStart(
            UUID personalAccountId,
            QuotaPeriodType periodType,
            LocalDate periodStart);
}
