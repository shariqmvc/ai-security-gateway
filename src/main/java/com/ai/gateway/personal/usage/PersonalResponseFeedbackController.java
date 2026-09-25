package com.ai.gateway.personal.usage;

import com.ai.gateway.authentication.*;
import com.ai.gateway.personal.usage.dto.PersonalResponseFeedbackRequest;
import com.ai.gateway.personal.usage.service.PersonalResponseFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/personal/requests/{requestId}/feedback")
@RequiredArgsConstructor
public class PersonalResponseFeedbackController {
 private final PersonalResponseFeedbackService service;

 @PutMapping
 public void rate(HttpServletRequest request,@PathVariable UUID requestId,@Valid @RequestBody PersonalResponseFeedbackRequest body){
  service.rate(context(request).getPersonalAccountId(),requestId,body.getRating());
 }

 @DeleteMapping
 public void remove(HttpServletRequest request,@PathVariable UUID requestId){
  service.remove(context(request).getPersonalAccountId(),requestId);
 }

 private AuthenticationContext context(HttpServletRequest request){
  AuthenticationContext c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
  if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null
     ||c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)
   throw new AccessDeniedException("Personal session authentication is required.");
  return c;
 }
}
