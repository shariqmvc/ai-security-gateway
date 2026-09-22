package com.ai.gateway.personal.intelligence;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.slf4j.MDC;
import com.ai.gateway.core.observability.RequestCorrelationFilter;

@RestController
@RequestMapping("/api/personal/security")
@RequiredArgsConstructor
public class PersonalSecurityIntelligenceController {

    private final PersonalSecurityIntelligenceService service;

    @PostMapping("/assess")
    public PersonalSecurityAssessment assess(
            HttpServletRequest request,
            @RequestBody SecurityAssessmentRequest body) {

        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);

        if (context == null || !context.isPersonalPrincipal()) {
            throw new AccessDeniedException(
                    "Personal authentication is required.");
        }

        UUID requestId = resolveRequestId(request);
        return service.assess(requestId, body == null ? null : body.prompt());
    }

    private UUID resolveRequestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-ID");
        if (value == null) {
            value = MDC.get(RequestCorrelationFilter.REQUEST_ID);
        }
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // The request correlation filter normally supplies a valid id.
            }
        }
        return UUID.randomUUID();
    }

    public record SecurityAssessmentRequest(String prompt) {}
}
