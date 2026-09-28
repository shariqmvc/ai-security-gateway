package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/auth/personal")
@RequiredArgsConstructor
public class PersonalAuthController {

    private final PersonalAuthService personalAuthService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalSignupResponse signup(
            @Valid @RequestBody PersonalSignupRequest request) {
        return personalAuthService.signup(request);
    }

    @PostMapping("/login")
    public PersonalLoginResponse login(
            @Valid @RequestBody PersonalLoginRequest request) {
        return personalAuthService.login(request);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(
            @jakarta.validation.Valid
            @RequestBody PersonalVerifyEmailRequest request) {
        personalAuthService.verifyEmail(request.token());
    }

    @PostMapping("/verify-phone/send")
    public PersonalVerificationResponse sendPhoneVerification(
            HttpServletRequest request,
            @Valid @RequestBody PersonalPhoneRequest body) {
        return personalAuthService.sendPhoneVerification(body);
    }

    @PostMapping("/verify-phone")
    public PersonalVerificationResponse verifyPhone(
            HttpServletRequest request,
            @Valid @RequestBody PersonalVerifyPhoneRequest body) {
        return personalAuthService.verifyPhone(body);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        personalAuthService.logout(request);
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext context = (AuthenticationContext) request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if (context == null || !context.isPersonalPrincipal()) {
            throw new org.springframework.security.access.AccessDeniedException("Personal authentication is required.");
        }
        return context;
    }
}
