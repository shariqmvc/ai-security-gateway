package com.ai.gateway.personal.quota.service;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.*;
import java.util.UUID;

public interface PersonalQuotaService {
 void beforeRequest(AuthenticationContext auth,long estimatedInputTokens);
 void afterSuccess(AuthenticationContext auth,AIResponse response);
 void release(AuthenticationContext auth);
 long activeRequests(UUID accountId);
}
