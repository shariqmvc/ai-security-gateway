package com.ai.gateway.personal.chat.repository;

import com.ai.gateway.personal.chat.entity.PersonalChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalChatSessionRepository extends JpaRepository<PersonalChatSession, UUID> {
    List<PersonalChatSession> findByPersonalAccountIdOrderByUpdatedAtDesc(UUID personalAccountId);
    Optional<PersonalChatSession> findByIdAndPersonalAccountId(UUID id, UUID personalAccountId);
}
