package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalLoginRequest;
import com.ai.gateway.personal.dto.PersonalLoginResponse;
import com.ai.gateway.personal.dto.PersonalSignupRequest;
import com.ai.gateway.personal.dto.PersonalSignupResponse;
import com.ai.gateway.personal.dto.PersonalUserResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface PersonalAuthService {

    PersonalSignupResponse signup(PersonalSignupRequest request);

    PersonalLoginResponse login(PersonalLoginRequest request);

    void verifyEmail(String token);

    AuthenticationContext authenticateBearer(String token);

    PersonalUserResponse me(AuthenticationContext context);

    void logout(HttpServletRequest request);
}
