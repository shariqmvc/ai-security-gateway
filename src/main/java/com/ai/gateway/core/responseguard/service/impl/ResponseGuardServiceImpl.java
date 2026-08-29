package com.ai.gateway.core.responseguard.service.impl;

import com.ai.gateway.core.responseguard.ResponseResult;
import com.ai.gateway.core.responseguard.service.ResponseGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponseGuardServiceImpl implements ResponseGuardService {
    @Override
    public ResponseResult inspect(String response) {
        return null;
    }
}
