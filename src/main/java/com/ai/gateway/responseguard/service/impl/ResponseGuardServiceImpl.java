package com.ai.gateway.responseguard.service.impl;

import com.ai.gateway.responseguard.ResponseResult;
import com.ai.gateway.responseguard.service.ResponseGuardService;
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
