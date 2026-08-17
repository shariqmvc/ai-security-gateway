package com.ai.gateway.service.impl;

import com.ai.gateway.enums.PIIType;
import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.service.PIIDetectionService;
import com.ai.gateway.util.RegexUtil;
import com.ai.gateway.util.TokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Slf4j
@Service
public class PIIDetectionServiceImpl implements PIIDetectionService {
    public MaskingResult mask(String prompt) {

        TokenGenerator tokenGenerator = new TokenGenerator();

        List<DetectedPII> detectedValues = new ArrayList<>();

        String masked = prompt;

        masked = mask(masked,
                RegexUtil.EMAIL_PATTERN,
                PIIType.EMAIL,
                tokenGenerator,
                detectedValues);

        masked = mask(masked,
                RegexUtil.PHONE_PATTERN,
                PIIType.PHONE,
                tokenGenerator,
                detectedValues);

        masked = mask(masked,
                RegexUtil.CREDIT_CARD_PATTERN,
                PIIType.CREDIT_CARD,
                tokenGenerator,
                detectedValues);

        return MaskingResult.builder()
                .maskedPrompt(masked)
                .detectedValues(detectedValues)
                .build();
    }

    private String mask(String input,
                        Pattern pattern,
                        PIIType piiType,
                        TokenGenerator tokenGenerator,
                        List<DetectedPII> detectedValues) {

        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }

        StringBuffer buffer = new StringBuffer(input.length());
        do {
            String original = matcher.group();
            String token = tokenGenerator.nextToken(piiType);

            detectedValues.add(
                    DetectedPII.builder()
                            .originalValue(original)
                            .token(token)
                            .piiType(piiType)
                            .build());

            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(token));
        } while (matcher.find());

        matcher.appendTail(buffer);

        return buffer.toString();
    }

}
