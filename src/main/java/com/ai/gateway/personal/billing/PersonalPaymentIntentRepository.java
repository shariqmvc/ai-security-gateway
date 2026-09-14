package com.ai.gateway.personal.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PersonalPaymentIntentRepository extends JpaRepository<PersonalPaymentIntent, UUID> {
 Optional<PersonalPaymentIntent> findByIdempotencyKey(String idempotencyKey);
 Optional<PersonalPaymentIntent> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
