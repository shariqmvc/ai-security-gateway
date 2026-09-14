package com.ai.gateway.personal.apikey.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.apikey.dto.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PersonalApiKeyService {
    PersonalApiKeyCreateResponse create(AuthenticationContext sessionContext, PersonalApiKeyCreateRequest request);
    List<PersonalApiKeyResponse> list(AuthenticationContext sessionContext);
    void revoke(AuthenticationContext sessionContext, UUID keyId);
    PersonalApiKeyCreateResponse rotate(AuthenticationContext sessionContext, UUID keyId);
    AuthenticationContext authenticate(String rawKey);
    boolean hasScope(AuthenticationContext context, String scope);
    Set<String> scopes(AuthenticationContext context);
}
