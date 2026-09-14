package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.credit.service.PersonalCreditService;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalPaymentServiceImpl implements PersonalPaymentService {
 private final PersonalPaymentIntentRepository intents;
 private final PersonalPaymentWebhookEventRepository events;
 private final PersonalPaymentProperties properties;
 private final PersonalCreditService credits;

 @Override @Transactional
 public PersonalPaymentIntent createIntent(UUID accountId, String packageCode, String idempotencyKey) {
  if(accountId==null) throw new PersonalCreditException("Personal account id is required.");
  if(idempotencyKey==null || idempotencyKey.isBlank() || idempotencyKey.length()>128) throw new PersonalCreditException("A valid idempotency key is required.");
  var existing=intents.findByIdempotencyKey(idempotencyKey);
  if(existing.isPresent()){ if(!existing.get().getPersonalAccountId().equals(accountId)) throw new PersonalCreditException("Idempotency key belongs to another Personal account."); return existing.get(); }
  var pack=properties.getPackages().get(packageCode);
  if(pack==null || pack.amount()==null || pack.credits()==null || pack.amount().signum()<=0 || pack.credits().signum()<=0) throw new PersonalCreditException("Unknown or invalid credit package: "+packageCode);
  var now=LocalDateTime.now();
  return intents.save(PersonalPaymentIntent.builder().personalAccountId(accountId).packageCode(packageCode).currency(properties.getCurrency()).amount(pack.amount()).credits(pack.credits()).provider(properties.getProvider()).status(PersonalPaymentStatus.PENDING).idempotencyKey(idempotencyKey).createdAt(now).updatedAt(now).build());
 }

 @Override @Transactional
 public void processWebhook(String signature,String provider,String eventId,String eventType,UUID paymentIntentId,String providerPaymentId){
  if(!constantTimeEquals(sign(eventId,eventType,paymentIntentId,providerPaymentId),signature)) throw new PersonalCreditException("Invalid payment webhook signature.");
  if(eventId==null||eventId.isBlank()) throw new PersonalCreditException("Payment event id is required.");
  if(events.findByEventId(eventId).isPresent()) return;
  var event=events.save(PersonalPaymentWebhookEvent.builder().provider(provider).eventId(eventId).eventType(eventType).receivedAt(LocalDateTime.now()).status("RECEIVED").build());
  var intent=intents.findById(paymentIntentId).orElseThrow(()->new PersonalCreditException("Payment intent not found."));
  if(!intent.getProvider().equals(provider)) throw new PersonalCreditException("Payment provider mismatch.");
  if(providerPaymentId!=null && !providerPaymentId.isBlank()) intent.setProviderPaymentId(providerPaymentId);
  if("PAYMENT_SUCCEEDED".equals(eventType)) {
    if(intent.getStatus()==PersonalPaymentStatus.SUCCEEDED){ event.setStatus("DUPLICATE"); event.setProcessedAt(LocalDateTime.now()); events.save(event); return; }
    if(intent.getStatus()!=PersonalPaymentStatus.PENDING) throw new PersonalCreditException("Payment intent is not payable.");
    credits.credit(intent.getPersonalAccountId(),intent.getCredits(),"payment:"+intent.getId(),"Credit purchase "+intent.getPackageCode());
    intent.setStatus(PersonalPaymentStatus.SUCCEEDED); intent.setCompletedAt(LocalDateTime.now());
  } else if("PAYMENT_FAILED".equals(eventType)) { intent.setStatus(PersonalPaymentStatus.FAILED); intent.setCompletedAt(LocalDateTime.now());
  } else if("PAYMENT_CANCELED".equals(eventType)) { intent.setStatus(PersonalPaymentStatus.CANCELED); intent.setCompletedAt(LocalDateTime.now());
  } else if("PAYMENT_REFUNDED".equals(eventType)) {
    if(intent.getStatus()==PersonalPaymentStatus.REFUNDED){ event.setStatus("DUPLICATE"); event.setProcessedAt(LocalDateTime.now()); events.save(event); return; }
    if(intent.getStatus()!=PersonalPaymentStatus.SUCCEEDED) throw new PersonalCreditException("Only a successful payment can be refunded.");
    credits.debit(intent.getPersonalAccountId(), intent.getCredits(), "refund:"+intent.getId(),
            "Credit purchase refund " + intent.getPackageCode());
    intent.setStatus(PersonalPaymentStatus.REFUNDED); intent.setCompletedAt(LocalDateTime.now());
  } else throw new PersonalCreditException("Unsupported payment event type: "+eventType);
  intent.setUpdatedAt(LocalDateTime.now()); intents.save(intent); event.setStatus("PROCESSED"); event.setProcessedAt(LocalDateTime.now()); events.save(event);
 }
 private String sign(String eventId,String eventType,UUID intentId,String providerPaymentId){
  try{Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256")); String payload=eventId+"."+eventType+"."+intentId+"."+(providerPaymentId==null?"":providerPaymentId); return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Payment webhook signing is unavailable.",e);}
 }
 private boolean constantTimeEquals(String a,String b){return a!=null&&b!=null&&java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
