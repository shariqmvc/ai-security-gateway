package com.ai.gateway.personal.usage;
import com.ai.gateway.authentication.*;
import com.ai.gateway.personal.usage.dto.*;
import com.ai.gateway.personal.usage.service.PersonalRequestHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
public class PersonalUsageController {
 private final PersonalRequestHistoryService historyService;

 @GetMapping("/requests")
 public Page<PersonalRequestHistoryResponse> requests(
   HttpServletRequest request,
   @RequestParam(defaultValue="0") int page,
   @RequestParam(defaultValue="25") int size) {
  AuthenticationContext auth=context(request);
  int safeSize=Math.min(Math.max(size,1),100);
  return historyService.list(auth.getPersonalAccountId(),PageRequest.of(Math.max(page,0),safeSize));
 }

 @GetMapping("/requests/{requestId}")
 public PersonalRequestHistoryResponse request(HttpServletRequest request,@PathVariable UUID requestId){
  return historyService.get(context(request).getPersonalAccountId(),requestId);
 }

 @GetMapping("/usage/summary")
 public PersonalUsageSummaryResponse summary(HttpServletRequest request){
  return historyService.summary(context(request).getPersonalAccountId());
 }

 private AuthenticationContext context(HttpServletRequest request){
  AuthenticationContext c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
  if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null
     || c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)
   throw new AccessDeniedException("Personal session authentication is required.");
  return c;
 }
}
