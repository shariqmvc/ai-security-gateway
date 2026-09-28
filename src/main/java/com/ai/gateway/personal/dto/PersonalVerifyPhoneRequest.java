package com.ai.gateway.personal.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record PersonalVerifyPhoneRequest(
    @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Verification code must contain 6 digits")
    String code) {}