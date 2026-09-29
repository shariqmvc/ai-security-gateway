package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.*;
import jakarta.servlet.http.HttpServletRequest;

public interface PersonalAuthService {

    PersonalSignupResponse signup(PersonalSignupRequest request);

    PersonalLoginResponse login(PersonalLoginRequest request);

    void verifyEmail(String token);

    PersonalVerificationResponse sendPhoneVerification(PersonalPhoneRequest request);

    PersonalVerificationResponse verifyPhone(PersonalVerifyPhoneRequest request);

    AuthenticationContext authenticateBearer(String token);

    PersonalUserResponse me(AuthenticationContext context);

    void logout(HttpServletRequest request);
}
