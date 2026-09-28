package com.ai.gateway.personal.dto;
public record PersonalVerificationResponse(boolean verified, boolean required, String message, String verificationCode) {}