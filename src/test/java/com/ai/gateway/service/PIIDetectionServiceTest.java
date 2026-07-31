package com.ai.gateway.service;

import com.ai.gateway.dto.MaskingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
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

        assertTrue(result.getMaskedPrompt().contains("{{EMAIL_1}}"));
        assertTrue(result.getMaskedPrompt().contains("{{PHONE_1}}"));
        assertEquals(2, result.getDetectedValues().size());
    }
}
