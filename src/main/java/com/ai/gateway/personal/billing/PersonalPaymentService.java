package com.ai.gateway.personal.billing;

import java.util.UUID;

public interface PersonalPaymentService {
 default PersonalPaymentIntent createIntent(UUID accountId,String packageCode,String idempotencyKey){return createIntent(accountId,packageCode,idempotencyKey,null);}
 PersonalPaymentIntent createIntent(UUID accountId,String packageCode,String idempotencyKey,java.math.BigDecimal creditAmount);
 void processWebhook(String signature, String provider, String eventId, String eventType, UUID paymentIntentId, String providerPaymentId);
 java.util.List<PersonalPaymentIntent> history(UUID accountId);
 void processStripeWebhook(String signature, String payload);
}
