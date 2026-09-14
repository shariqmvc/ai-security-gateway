package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.credit.service.PersonalCreditService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonalPaymentServiceImplTest {
 @Test void idempotencyReturnsExistingIntent(){
  var intents=mock(PersonalPaymentIntentRepository.class); var events=mock(PersonalPaymentWebhookEventRepository.class); var props=new PersonalPaymentProperties(); props.getPackages().put("starter",new PersonalPaymentProperties.CreditPackage(new BigDecimal("5"),new BigDecimal("5"))); var credits=mock(PersonalCreditService.class); var id=UUID.randomUUID(); var existing=PersonalPaymentIntent.builder().id(id).personalAccountId(UUID.randomUUID()).idempotencyKey("k").build(); when(intents.findByIdempotencyKey("k")).thenReturn(java.util.Optional.of(existing)); var s=new PersonalPaymentServiceImpl(intents,events,props,credits); assertSame(existing,s.createIntent(existing.getPersonalAccountId(),"starter","k")); verify(intents,never()).save(any()); }
 @Test void successfulWebhookCreditsExactlyOnce(){
  var intents=mock(PersonalPaymentIntentRepository.class); var events=mock(PersonalPaymentWebhookEventRepository.class); var props=new PersonalPaymentProperties(); props.setProvider("EXTERNAL"); props.setWebhookSecret("secret"); var credits=mock(PersonalCreditService.class); var aid=UUID.randomUUID(); var iid=UUID.randomUUID(); var intent=PersonalPaymentIntent.builder().id(iid).personalAccountId(aid).packageCode("starter").credits(new BigDecimal("5")).provider("EXTERNAL").status(PersonalPaymentStatus.PENDING).build(); when(events.findByEventId("e1")).thenReturn(java.util.Optional.empty()); when(events.save(any())).thenAnswer(i->i.getArgument(0)); when(intents.findById(iid)).thenReturn(java.util.Optional.of(intent)); when(intents.save(any())).thenAnswer(i->i.getArgument(0)); var s=new PersonalPaymentServiceImpl(intents,events,props,credits); String sig=sign("secret","e1","PAYMENT_SUCCEEDED",iid,"pp1"); s.processWebhook(sig,"EXTERNAL","e1","PAYMENT_SUCCEEDED",iid,"pp1"); verify(credits).credit(aid,new BigDecimal("5"),"payment:"+iid,"Credit purchase starter"); assertEquals(PersonalPaymentStatus.SUCCEEDED,intent.getStatus()); }
 private String sign(String secret,String eventId,String eventType,UUID id,String providerPaymentId){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));var p=eventId+"."+eventType+"."+id+"."+providerPaymentId;return java.util.Base64.getEncoder().encodeToString(mac.doFinal(p.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}} @Test void successfulRefundReversesIssuedCredits(){
  var intents=mock(PersonalPaymentIntentRepository.class); var events=mock(PersonalPaymentWebhookEventRepository.class);
  var props=new PersonalPaymentProperties(); props.setProvider("EXTERNAL"); props.setWebhookSecret("secret");
  var credits=mock(PersonalCreditService.class); var aid=UUID.randomUUID(); var iid=UUID.randomUUID();
  var intent=PersonalPaymentIntent.builder().id(iid).personalAccountId(aid).packageCode("starter")
      .credits(new BigDecimal("5")).provider("EXTERNAL").status(PersonalPaymentStatus.SUCCEEDED).build();
  when(events.findByEventId("e-ref")).thenReturn(java.util.Optional.empty());
  when(events.save(any())).thenAnswer(i->i.getArgument(0)); when(intents.findById(iid)).thenReturn(java.util.Optional.of(intent));
  when(intents.save(any())).thenAnswer(i->i.getArgument(0));
  var s=new PersonalPaymentServiceImpl(intents,events,props,credits);
  String sig=sign("secret","e-ref","PAYMENT_REFUNDED",iid,"pp1");
  s.processWebhook(sig,"EXTERNAL","e-ref","PAYMENT_REFUNDED",iid,"pp1");
  verify(credits).debit(aid,new BigDecimal("5"),"refund:"+iid,"Credit purchase refund starter");
  assertEquals(PersonalPaymentStatus.REFUNDED,intent.getStatus());
 }

}
