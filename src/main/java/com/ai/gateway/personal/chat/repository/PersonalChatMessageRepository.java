package com.ai.gateway.personal.chat.repository;

import com.ai.gateway.personal.chat.entity.PersonalChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PersonalChatMessageRepository extends JpaRepository<PersonalChatMessage, UUID> {
    List<PersonalChatMessage> findBySessionIdOrderBySequenceNoAsc(UUID sessionId);

    @Transactional
    void deleteBySessionId(UUID sessionId);
}
