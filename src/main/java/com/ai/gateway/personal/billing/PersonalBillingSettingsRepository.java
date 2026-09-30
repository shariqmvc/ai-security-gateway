package com.ai.gateway.personal.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PersonalBillingSettingsRepository extends JpaRepository<PersonalBillingSettings,UUID> {
 Optional<PersonalBillingSettings> findByPersonalAccountId(UUID personalAccountId);
}