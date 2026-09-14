package com.ai.gateway.personal.intelligence;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

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

        return service.assess(body == null ? null : body.prompt());
    }

    public record SecurityAssessmentRequest(String prompt) {}
}
