package com.ai.gateway.service.impl;

import com.ai.gateway.service.OpenAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAIServiceImpl implements OpenAIService {
    @Override
    public String ask(String maskedPrompt) {
        // Phase 1
        return maskedPrompt;
    }
}
