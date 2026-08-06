package com.ai.gateway.authentication;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationService {

    AuthenticationResult authenticate(
            HttpServletRequest request);

}
