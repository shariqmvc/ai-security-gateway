package com.ai.gateway.responseguard.service;

import com.ai.gateway.responseguard.ResponseResult;

public interface ResponseGuardService {
    ResponseResult inspect(String response);
}
