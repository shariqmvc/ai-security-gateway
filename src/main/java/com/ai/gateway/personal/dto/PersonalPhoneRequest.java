package com.ai.gateway.personal.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record PersonalPhoneRequest(
    @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Use an international phone number, e.g. +919876543210")
    String phoneNumber) {}