package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/personal/billing")
public class PersonalPaymentController {
 private final PersonalPaymentService service;
 private final PersonalBillingSettingsService settingsService;

 @PostMapping("/intents")
 public PersonalPaymentIntent create(@RequestBody CreateIntentRequest body,HttpServletRequest request){
  return service.createIntent(context(request).getPersonalAccountId(),body.packageCode(),body.idempotencyKey(),body.creditAmount());
 }

 @GetMapping("/history")
 public java.util.List<PersonalPaymentIntent> history(HttpServletRequest request){
  return service.history(context(request).getPersonalAccountId());
 }

 @GetMapping("/settings")
 public PersonalBillingSettings settings(HttpServletRequest request){return settingsService.get(context(request));}

 @PutMapping("/settings")
 public PersonalBillingSettings updateSettings(@RequestBody PersonalBillingSettingsService.UpdateRequest body,HttpServletRequest request){
  return settingsService.update(context(request),body);
 }

 @PostMapping(value="/webhooks/stripe",consumes="application/json")
 public void stripeWebhook(@RequestHeader("Stripe-Signature") String signature,@RequestBody String payload){
  service.processStripeWebhook(signature,payload);
 }

 @PostMapping("/webhooks")
 public void webhook(@RequestHeader("X-AR-Payment-Signature") String signature,@RequestBody WebhookRequest body){
  service.processWebhook(signature,body.provider(),body.eventId(),body.eventType(),body.paymentIntentId(),body.providerPaymentId());
 }

 private AuthenticationContext context(HttpServletRequest request){
  var c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
  if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null||c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)
   throw new AccessDeniedException("Personal session authentication is required for billing.");
  return c;
 }
 public record CreateIntentRequest(String packageCode,String idempotencyKey,java.math.BigDecimal creditAmount){}
 public record WebhookRequest(String provider,String eventId,String eventType,UUID paymentIntentId,String providerPaymentId){}
}