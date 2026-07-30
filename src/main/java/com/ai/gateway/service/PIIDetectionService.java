package com.ai.gateway.service;

import com.ai.gateway.dto.MaskingResult;

public interface PIIDetectionService {
    MaskingResult mask(String prompt);
}
