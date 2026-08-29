package com.ai.gateway.personal.repository;

import com.ai.gateway.personal.entity.PersonalEmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonalEmailVerificationTokenRepository
        extends JpaRepository<PersonalEmailVerificationToken, UUID> {

    Optional<PersonalEmailVerificationToken> findByTokenHash(String tokenHash);
}
