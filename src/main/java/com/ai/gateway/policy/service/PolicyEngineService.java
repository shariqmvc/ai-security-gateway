package com.ai.gateway.policy.service;

import com.ai.gateway.policy.PolicyResult;

public interface PolicyEngineService {

    PolicyResult evaluate(String prompt);
}
