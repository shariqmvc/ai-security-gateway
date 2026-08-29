package com.ai.gateway.core.responseguard.service;

import com.ai.gateway.core.responseguard.ResponseResult;

public interface ResponseGuardService {
    ResponseResult inspect(String response);
}
