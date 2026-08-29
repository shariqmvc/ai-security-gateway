package com.ai.gateway.personal.repository;

import com.ai.gateway.personal.entity.PersonalSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PersonalSessionRepository extends JpaRepository<PersonalSession, UUID> {

    Optional<PersonalSession> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
