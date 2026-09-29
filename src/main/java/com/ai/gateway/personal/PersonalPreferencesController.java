package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalPreferencesResponse;
import com.ai.gateway.personal.dto.PersonalPreferencesUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personal/settings/preferences")
@RequiredArgsConstructor
public class PersonalPreferencesController {

    private final PersonalPreferencesService preferencesService;

    @GetMapping
    public PersonalPreferencesResponse get(HttpServletRequest request) {
        return preferencesService.get(context(request));
    }

    @PutMapping
    public PersonalPreferencesResponse update(
            HttpServletRequest request,
            @Valid @RequestBody PersonalPreferencesUpdateRequest body) {
        return preferencesService.update(context(request), body);
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);

        if (context == null || !context.isPersonalPrincipal()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Personal authentication is required.");
        }

        return context;
    }
}
