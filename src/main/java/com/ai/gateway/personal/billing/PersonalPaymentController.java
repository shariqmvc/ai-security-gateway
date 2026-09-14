package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/personal/billing")
public class PersonalPaymentController {
 private final PersonalPaymentService service;
 @PostMapping("/intents")
 public PersonalPaymentIntent create(@RequestBody CreateIntentRequest body,HttpServletRequest request){ return service.createIntent(context(request).getPersonalAccountId(),body.packageCode(),body.idempotencyKey()); }
 @PostMapping("/webhooks")
 public void webhook(@RequestHeader("X-AR-Payment-Signature") String signature,@RequestBody WebhookRequest body){ service.processWebhook(signature,body.provider(),body.eventId(),body.eventType(),body.paymentIntentId(),body.providerPaymentId()); }
 private AuthenticationContext context(HttpServletRequest request){var c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null
   || c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)
  throw new AccessDeniedException("Personal session authentication is required for billing.");return c;}
 public record CreateIntentRequest(String packageCode,String idempotencyKey){}
 public record WebhookRequest(String provider,String eventId,String eventType,UUID paymentIntentId,String providerPaymentId){}
}
