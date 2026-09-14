package com.ai.gateway.personal.apikey;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.apikey.dto.PersonalApiKeyCreateRequest;
import com.ai.gateway.personal.apikey.dto.PersonalApiKeyCreateResponse;
import com.ai.gateway.personal.apikey.dto.PersonalApiKeyResponse;
import com.ai.gateway.personal.apikey.service.PersonalApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/personal/api-keys")
@RequiredArgsConstructor
public class PersonalApiKeyController {
    private final PersonalApiKeyService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalApiKeyCreateResponse create(
            HttpServletRequest request,
            @Valid @RequestBody PersonalApiKeyCreateRequest body) {
        return service.create(session(request), body);
    }

    @GetMapping
    public List<PersonalApiKeyResponse> list(HttpServletRequest request) {
        return service.list(session(request));
    }

    @PostMapping("/{id}/rotate")
    public PersonalApiKeyCreateResponse rotate(
            HttpServletRequest request,
            @PathVariable UUID id) {
        return service.rotate(session(request), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(HttpServletRequest request, @PathVariable UUID id) {
        service.revoke(session(request), id);
    }

    private AuthenticationContext session(HttpServletRequest request) {
        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if (context == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required.");
        }
        return context;
    }
}
