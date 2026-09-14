package com.ai.gateway.personal.billing;

import java.util.UUID;

public interface PersonalPaymentService {
 PersonalPaymentIntent createIntent(UUID accountId, String packageCode, String idempotencyKey);
 void processWebhook(String signature, String provider, String eventId, String eventType, UUID paymentIntentId, String providerPaymentId);
}
