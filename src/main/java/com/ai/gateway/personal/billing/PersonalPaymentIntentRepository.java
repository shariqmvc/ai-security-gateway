package com.ai.gateway.personal.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PersonalPaymentIntentRepository extends JpaRepository<PersonalPaymentIntent, UUID> {
 Optional<PersonalPaymentIntent> findByIdempotencyKey(String idempotencyKey);
 List<PersonalPaymentIntent> findByPersonalAccountIdOrderByCreatedAtDesc(UUID personalAccountId);
 Optional<PersonalPaymentIntent> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
