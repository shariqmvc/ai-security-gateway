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

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        personalAuthService.logout(request);
    }

}
