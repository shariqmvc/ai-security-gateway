package com.ai.gateway.personal.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PersonalPaymentWebhookEventRepository extends JpaRepository<PersonalPaymentWebhookEvent, UUID> {
 Optional<PersonalPaymentWebhookEvent> findByEventId(String eventId);
}
