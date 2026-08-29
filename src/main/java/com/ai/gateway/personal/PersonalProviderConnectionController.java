package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/personal/providers")
@RequiredArgsConstructor
public class PersonalProviderConnectionController {

    private final PersonalProviderConnectionService service;

    @GetMapping
    public List<PersonalProviderConnectionResponse> list(
            HttpServletRequest request) {
        return service.list(context(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalProviderConnectionResponse connect(
            HttpServletRequest request,
            @Valid @RequestBody PersonalProviderConnectRequest body) {
        return service.connect(context(request), body);
    }

    @PostMapping("/{provider}/validate")
    public PersonalProviderValidationResponse validate(
            HttpServletRequest request,
            @PathVariable Provider provider) {
        return service.validate(context(request), provider);
    }

    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(
            HttpServletRequest request,
            @PathVariable Provider provider) {
        service.disconnect(context(request), provider);
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
