package com.ai.gateway.personal.credit.exception;

/**
 * Raised when a Personal CREDIT request cannot be funded by the available
 * wallet balance. This is a normal billing condition and maps to HTTP 402.
 */
public class PersonalInsufficientCreditsException extends PersonalCreditException {

    public PersonalInsufficientCreditsException(String message) {
        super(message);
    }
}
