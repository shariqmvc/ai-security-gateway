package com.ai.gateway.service;

import com.ai.gateway.dto.MaskingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PIIDetectionServiceTest {

    @Autowired
    private PIIDetectionService service;

    @Test
    void shouldMaskEmailAndPhone() {

        String prompt = "Email john@gmail.com Phone 9876543210";

        MaskingResult result = service.mask(prompt);

        System.out.println("Original Prompt : " + prompt);
        System.out.println("Masked Prompt   : " + result.getMaskedPrompt());
        System.out.println("Detected Values : " + result.getDetectedValues());

        assertTrue(
                result.getMaskedPrompt()
                        .contains("<PII_EMAIL_"));

        assertTrue(
                result.getMaskedPrompt()
                        .contains("<PII_PHONE_"));

        assertFalse(
                result.getMaskedPrompt()
                        .contains("john@gmail.com"));

        assertFalse(
                result.getMaskedPrompt()
                        .contains("9876543210"));

        assertEquals(
                2,
                result.getDetectedValues().size());
        assertEquals(2, result.getDetectedValues().size());
    }
}
