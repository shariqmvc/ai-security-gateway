package com.ai.gateway.service.impl;

import com.ai.gateway.constants.PIIType;
import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.service.PIIDetectionService;
import com.ai.gateway.util.RegexUtil;
import com.ai.gateway.util.TokenGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PIIDetectionServiceImpl implements PIIDetectionService {
    public MaskingResult mask(String prompt) {

        TokenGenerator generator = new TokenGenerator();
        List<DetectedPII> detectedValues = new ArrayList<>();

        String maskedPrompt = prompt;

        maskedPrompt = maskPattern(
                maskedPrompt,
                RegexUtil.EMAIL_PATTERN,
                PIIType.EMAIL,
                detectedValues,
                generator);

        maskedPrompt = maskPattern(
                maskedPrompt,
                RegexUtil.PHONE_PATTERN,
                PIIType.PHONE,
                detectedValues,
                generator);

        maskedPrompt = maskPattern(
                maskedPrompt,
                RegexUtil.CREDIT_CARD_PATTERN,
                PIIType.CREDIT_CARD,
                detectedValues,
                generator);

        return MaskingResult.builder()
                .maskedPrompt(maskedPrompt)
                .detectedValues(detectedValues)
                .build();
    }

    private String maskPattern(String text,
                               Pattern pattern,
                               PIIType piiType,
                               List<DetectedPII> detectedValues,
                               TokenGenerator generator) {

        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {

            String value = matcher.group();
            String token = generator.nextToken(piiType);

            detectedValues.add(
                    DetectedPII.builder()
                            .originalValue(value)
                            .token(token)
                            .piiType(piiType)
                            .build()
            );

            matcher.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(token)
            );
        }

        matcher.appendTail(sb);

        return sb.toString();
    }
}
