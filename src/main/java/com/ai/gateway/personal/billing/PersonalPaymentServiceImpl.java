package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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
 private final StripeCheckoutClient stripe;
 private final ObjectMapper mapper;

 @Override
 @Transactional
 public PersonalPaymentIntent createIntent(UUID accountId,String packageCode,String idempotencyKey){return createIntent(accountId,packageCode,idempotencyKey,null);}

 @Override
 @Transactional
 public PersonalPaymentIntent createIntent(UUID accountId,String packageCode,String idempotencyKey,BigDecimal requestedCredits){
  if(accountId==null) throw new PersonalCreditException("Personal account id is required.");
  if(idempotencyKey==null||idempotencyKey.isBlank()||idempotencyKey.length()>128) throw new PersonalCreditException("A valid idempotency key is required.");
  var existing=intents.findByIdempotencyKey(idempotencyKey);
  if(existing.isPresent()){
   if(!existing.get().getPersonalAccountId().equals(accountId)) throw new PersonalCreditException("Idempotency key belongs to another Personal account.");
   return existing.get();
  }

  var pack=properties.getPackages().get(packageCode);
  BigDecimal creditsAmount=pack==null?null:pack.credits();
  BigDecimal baseAmount=pack==null?null:pack.amount();
  if("custom".equalsIgnoreCase(packageCode)){
   creditsAmount=requestedCredits;
   baseAmount=requestedCredits;
  }
  validateAmount(baseAmount,creditsAmount);

  BigDecimal fee=properties.feeFor(baseAmount);
  BigDecimal tax=BigDecimal.ZERO;
  BigDecimal total=baseAmount.add(fee).add(tax).setScale(2,java.math.RoundingMode.HALF_UP);
  var now=LocalDateTime.now();
  var intent=intents.save(PersonalPaymentIntent.builder()
    .personalAccountId(accountId).packageCode(packageCode).currency(properties.getCurrency())
    .amount(total).baseAmount(baseAmount).feeAmount(fee).taxAmount(tax).credits(creditsAmount)
    .provider(properties.getProvider()).status(PersonalPaymentStatus.PENDING)
    .idempotencyKey(idempotencyKey).createdAt(now).updatedAt(now).build());

  if("STRIPE".equalsIgnoreCase(properties.getProvider())){
   var session=stripe.createCheckoutSession(intent);
   intent.setCheckoutSessionId(session.id());
   intent.setProviderPaymentId(session.paymentIntentId());
   intent.setCheckoutUrl(session.url());
   if(session.invoiceId()!=null) intent.setInvoiceUrl(resolveInvoiceUrl(session.invoiceId()));
   intent.setUpdatedAt(LocalDateTime.now());
   intents.save(intent);
  }
  return intent;
 }

 @Transactional(readOnly=true)
 public java.util.List<PersonalPaymentIntent> history(UUID accountId){
  return intents.findByPersonalAccountIdOrderByCreatedAtDesc(accountId);
 }

 @Override
 @Transactional
 public void processWebhook(String signature,String provider,String eventId,String eventType,UUID paymentIntentId,String providerPaymentId){
  if(!constantTimeEquals(sign(eventId,eventType,paymentIntentId,providerPaymentId),signature)) throw new PersonalCreditException("Invalid payment webhook signature.");
  if(eventId==null||eventId.isBlank()) throw new PersonalCreditException("Payment event id is required.");
  if(events.findByEventId(eventId).isPresent()) return;
  var event=events.save(PersonalPaymentWebhookEvent.builder().provider(provider).eventId(eventId).eventType(eventType).receivedAt(LocalDateTime.now()).status("RECEIVED").build());
  var intent=intents.findById(paymentIntentId).orElseThrow(()->new PersonalCreditException("Payment intent not found."));
  if(!intent.getProvider().equals(provider)) throw new PersonalCreditException("Payment provider mismatch.");
  applyLegacyEvent(intent,event,eventType,providerPaymentId);
 }

 @Transactional
 public void processStripeWebhook(String signature,String payload){
  verifyStripeSignature(payload,signature);
  try{
   JsonNode root=mapper.readTree(payload);
   String eventId=text(root,"id"),type=text(root,"type");
   if(eventId==null||type==null) throw new PersonalCreditException("Invalid Stripe event.");
   if(events.findByEventId(eventId).isPresent()) return;
   JsonNode object=root.path("data").path("object");
   String intentId=object.path("metadata").path("intent_id").asText(null);
   if(intentId==null||intentId.isBlank()) intentId=object.path("client_reference_id").asText(null);
   var event=events.save(PersonalPaymentWebhookEvent.builder().provider("STRIPE").eventId(eventId).eventType(type).receivedAt(LocalDateTime.now()).status("RECEIVED").build());
   if(intentId==null){event.setStatus("IGNORED");event.setProcessedAt(LocalDateTime.now());events.save(event);return;}
   var intent=intents.findById(UUID.fromString(intentId)).orElseThrow(()->new PersonalCreditException("Payment intent not found."));
   if("checkout.session.completed".equals(type)||"checkout.session.async_payment_succeeded".equals(type)){
    if("paid".equalsIgnoreCase(text(object,"payment_status"))||"checkout.session.async_payment_succeeded".equals(type)){
     fulfill(intent,text(object,"payment_intent"),text(object,"invoice"));
    }
   }else if("checkout.session.async_payment_failed".equals(type)||"checkout.session.expired".equals(type)){
    if(intent.getStatus()==PersonalPaymentStatus.PENDING){intent.setStatus("checkout.session.expired".equals(type)?PersonalPaymentStatus.CANCELED:PersonalPaymentStatus.FAILED);intent.setCompletedAt(LocalDateTime.now());intent.setUpdatedAt(LocalDateTime.now());intents.save(intent);}
   }else if("charge.refunded".equals(type)){
    handleRefund(intent,object.path("amount_refunded").asLong(0),object.path("amount").asLong(0));
   }else if("payment_intent.succeeded".equals(type)){
    fulfill(intent,text(object,"id"),null);
   }else if("payment_intent.payment_failed".equals(type)){
    if(intent.getStatus()==PersonalPaymentStatus.PENDING){intent.setStatus(PersonalPaymentStatus.FAILED);intent.setCompletedAt(LocalDateTime.now());intent.setUpdatedAt(LocalDateTime.now());intents.save(intent);}
   }
   event.setStatus("PROCESSED");event.setProcessedAt(LocalDateTime.now());events.save(event);
  }catch(PersonalCreditException ex){throw ex;}
  catch(Exception ex){throw new PersonalCreditException("Unable to process Stripe webhook.");}
 }

 private void fulfill(PersonalPaymentIntent intent,String paymentIntentId,String invoiceId){
  if(paymentIntentId!=null) intent.setProviderPaymentId(paymentIntentId);
  if(invoiceId!=null) intent.setInvoiceUrl(resolveInvoiceUrl(invoiceId));
  if(intent.getStatus()==PersonalPaymentStatus.SUCCEEDED)return;
  if(intent.getStatus()!=PersonalPaymentStatus.PENDING)throw new PersonalCreditException("Payment intent is not payable.");
  credits.credit(intent.getPersonalAccountId(),intent.getCredits(),"payment:"+intent.getId(),"Credit purchase "+intent.getPackageCode());
  intent.setStatus(PersonalPaymentStatus.SUCCEEDED);intent.setCompletedAt(LocalDateTime.now());intent.setUpdatedAt(LocalDateTime.now());
  enrichReceipt(intent);
  intents.save(intent);
 }

 private void handleRefund(PersonalPaymentIntent intent,long refundedMinor,long chargedMinor){
  if(intent.getStatus()==PersonalPaymentStatus.REFUNDED)return;
  if(intent.getStatus()!=PersonalPaymentStatus.SUCCEEDED)return;
  if(refundedMinor<=0||chargedMinor<=0)return;
  BigDecimal ratio=BigDecimal.valueOf(refundedMinor).divide(BigDecimal.valueOf(chargedMinor),8,java.math.RoundingMode.HALF_UP).min(BigDecimal.ONE);
  BigDecimal refundCredits=intent.getCredits().multiply(ratio).setScale(8,java.math.RoundingMode.HALF_UP);
  try{
   credits.debit(intent.getPersonalAccountId(),refundCredits,"refund:"+intent.getId()+":"+refundedMinor,"Stripe refund for "+intent.getPackageCode());
   if(ratio.compareTo(BigDecimal.ONE)>=0){intent.setStatus(PersonalPaymentStatus.REFUNDED);intent.setCompletedAt(LocalDateTime.now());}
   intent.setUpdatedAt(LocalDateTime.now());intents.save(intent);
  }catch(RuntimeException ex){
   // Do not make Stripe retry forever because a user may already have spent the
   // credits. The event remains auditable for manual reconciliation.
   intent.setUpdatedAt(LocalDateTime.now());intents.save(intent);
  }
 }

 private void applyLegacyEvent(PersonalPaymentIntent intent,PersonalPaymentWebhookEvent event,String type,String providerPaymentId){
  if(providerPaymentId!=null&&!providerPaymentId.isBlank())intent.setProviderPaymentId(providerPaymentId);
  if("PAYMENT_SUCCEEDED".equals(type)){fulfill(intent,providerPaymentId,null);}
  else if("PAYMENT_FAILED".equals(type)){intent.setStatus(PersonalPaymentStatus.FAILED);intent.setCompletedAt(LocalDateTime.now());}
  else if("PAYMENT_CANCELED".equals(type)){intent.setStatus(PersonalPaymentStatus.CANCELED);intent.setCompletedAt(LocalDateTime.now());}
  else if("PAYMENT_REFUNDED".equals(type)){handleRefund(intent,minor(intent.getAmount()),minor(intent.getAmount()));}
  else throw new PersonalCreditException("Unsupported payment event type: "+type);
  intent.setUpdatedAt(LocalDateTime.now());intents.save(intent);event.setStatus("PROCESSED");event.setProcessedAt(LocalDateTime.now());events.save(event);
 }

 private String resolveInvoiceUrl(String invoiceId){
  try{JsonNode n=stripe.retrieve("/v1/invoices/"+invoiceId);return text(n,"hosted_invoice_url");}catch(Exception ignored){return null;}
 }
 private void enrichReceipt(PersonalPaymentIntent intent){
  try{
   if(intent.getProviderPaymentId()==null)return;
   JsonNode pi=stripe.retrieve("/v1/payment_intents/"+intent.getProviderPaymentId());
   String charge=text(pi,"latest_charge");
   if(charge!=null){JsonNode c=stripe.retrieve("/v1/charges/"+charge);intent.setReceiptUrl(text(c,"receipt_url"));}
  }catch(Exception ignored){}
 }
 private void verifyStripeSignature(String payload,String header){
  if(header==null||header.isBlank())throw new PersonalCreditException("Missing Stripe webhook signature.");
  long timestamp=0;java.util.List<String> signatures=new java.util.ArrayList<>();
  for(String part:header.split(",")){String[] kv=part.split("=",2);if(kv.length!=2)continue;if("t".equals(kv[0]))timestamp=Long.parseLong(kv[1]);if("v1".equals(kv[0]))signatures.add(kv[1]);}
  if(timestamp<=0||Math.abs(Instant.now().getEpochSecond()-timestamp)>300)throw new PersonalCreditException("Expired Stripe webhook signature.");
  String signed=timestamp+"."+payload;String expected=hmacHex(properties.getWebhookSecret(),signed);
  boolean match=signatures.stream().anyMatch(sig->MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),sig.getBytes(StandardCharsets.UTF_8)));
  if(!match)throw new PersonalCreditException("Invalid Stripe webhook signature.");
 }
 private String hmacHex(String secret,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] b=mac.doFinal(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private String sign(String eventId,String eventType,UUID intentId,String providerPaymentId){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String payload=eventId+"."+eventType+"."+intentId+"."+(providerPaymentId==null?"":providerPaymentId);return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Payment webhook signing is unavailable.",e);}}
 private boolean constantTimeEquals(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
 private long minor(BigDecimal v){return v.movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();}
 private void validateAmount(BigDecimal amount,BigDecimal creditsAmount){if(amount==null||creditsAmount==null||amount.signum()<=0||creditsAmount.signum()<=0)throw new PersonalCreditException("Unknown or invalid credit package.");}
 private String text(JsonNode n,String field){JsonNode v=n==null?null:n.get(field);return v==null||v.isNull()?null:v.asText();}
}