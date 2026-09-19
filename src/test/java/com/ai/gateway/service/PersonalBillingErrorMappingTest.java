package com.ai.gateway.service;

import com.ai.gateway.exception.ErrorResponse;
import com.ai.gateway.exception.GlobalExceptionHandler;
import com.ai.gateway.personal.credit.exception.PersonalInsufficientCreditsException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalBillingErrorMappingTest {

    @Test
    void insufficientCreditsMapsToPaymentRequired() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/chat");

        ResponseEntity<ErrorResponse> response = handler.handlePersonalInsufficientCredits(
                new PersonalInsufficientCreditsException("Insufficient AIRouter credits."),
                request);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertEquals(402, response.getBody().getStatus());
        assertEquals("Payment Required", response.getBody().getError());
        assertEquals("Insufficient AIRouter credits.", response.getBody().getMessage());
        assertEquals("/api/chat", response.getBody().getPath());
    }
}
