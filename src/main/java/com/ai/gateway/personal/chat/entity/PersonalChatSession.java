package com.ai.gateway.personal.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_CHAT_SESSIONS", indexes={
    @Index(name="idx_chat_session_account_updated", columnList="personal_account_id,updated_at"),
    @Index(name="idx_chat_session_parent", columnList="parent_session_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalChatSession {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name="personal_account_id", nullable=false)
    private UUID personalAccountId;

    @Column(nullable=false, length=255)
    private String title;

    @Column(name="parent_session_id")
    private UUID parentSessionId;

    @Column(name="branch_message_id")
    private UUID branchMessageId;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now=LocalDateTime.now();
        if(createdAt==null) createdAt=now;
        if(updatedAt==null) updatedAt=now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt=LocalDateTime.now();
    }
}
