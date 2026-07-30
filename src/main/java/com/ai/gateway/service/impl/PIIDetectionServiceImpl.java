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

@Service
public class PIIDetectionServiceImpl implements PIIDetectionService {
    public MaskingResult mask(String prompt) {

        TokenGenerator tokenGenerator = new TokenGenerator();

        List<DetectedPII> detectedValues = new ArrayList<>();

        String maskedPrompt = prompt;

        maskedPrompt = maskEmails(maskedPrompt, detectedValues, tokenGenerator);
        maskedPrompt = maskPhones(maskedPrompt, detectedValues, tokenGenerator);
        maskedPrompt = maskCards(maskedPrompt, detectedValues, tokenGenerator);

        return MaskingResult.builder()
                .maskedPrompt(maskedPrompt)
                .detectedValues(detectedValues)
                .build();
    }

    private String maskEmails(String text,
                              List<DetectedPII> detectedValues,
                              TokenGenerator generator) {

        Matcher matcher = RegexUtil.EMAIL_PATTERN.matcher(text);

        while (matcher.find()) {

            String value = matcher.group();

            String token = generator.nextToken(PIIType.EMAIL);

            detectedValues.add(
                    DetectedPII.builder()
                            .originalValue(value)
                            .token(token)
                            .piiType(PIIType.EMAIL)
                            .build()
            );

            text = text.replace(value, token);
        }

        return text;
    }

    private String maskPhones(String text,
                              List<DetectedPII> detectedValues,
                              TokenGenerator generator) {

        Matcher matcher = RegexUtil.PHONE_PATTERN.matcher(text);

        while (matcher.find()) {

            String value = matcher.group();

            String token = generator.nextToken(PIIType.PHONE);

            detectedValues.add(
                    DetectedPII.builder()
                            .originalValue(value)
                            .token(token)
                            .piiType(PIIType.PHONE)
                            .build()
            );

            text = text.replace(value, token);
        }

        return text;
    }

    private String maskCards(String text,
                             List<DetectedPII> detectedValues,
                             TokenGenerator generator) {

        Matcher matcher = RegexUtil.CREDIT_CARD_PATTERN.matcher(text);

        while (matcher.find()) {

            String value = matcher.group();

            String token = generator.nextToken(PIIType.CREDIT_CARD);

            detectedValues.add(
                    DetectedPII.builder()
                            .originalValue(value)
                            .token(token)
                            .piiType(PIIType.CREDIT_CARD)
                            .build()
            );

            text = text.replace(value, token);
        }

        return text;
    }
}
