package com.ai.gateway.personal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonalSignupRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 255) String password,
        @Size(max = 255) String displayName,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Use an international phone number, e.g. +919876543210")
        String phoneNumber) {
    public PersonalSignupRequest(String email, String password, String displayName) {
        this(email, password, displayName, null);
    }
}
