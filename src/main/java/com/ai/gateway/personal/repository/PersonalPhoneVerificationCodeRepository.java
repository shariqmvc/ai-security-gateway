package com.ai.gateway.personal.repository;

import com.ai.gateway.personal.entity.PersonalPhoneVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PersonalPhoneVerificationCodeRepository extends JpaRepository<PersonalPhoneVerificationCode, UUID> {
    Optional<PersonalPhoneVerificationCode> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);
}