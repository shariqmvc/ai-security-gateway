package com.ai.gateway.personal.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_CHAT_MESSAGES", indexes={
    @Index(name="idx_chat_message_session_sequence", columnList="session_id,sequence_no")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalChatMessage {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name="session_id", nullable=false)
    private UUID sessionId;

    @Column(nullable=false, length=16)
    private String role;

    @Column(nullable=false, columnDefinition="TEXT")
    private String content;

    @Column(name="request_id")
    private UUID requestId;

    @Column(name="sequence_no", nullable=false)
    private Integer sequenceNo;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if(createdAt==null) createdAt=LocalDateTime.now();
    }
}
