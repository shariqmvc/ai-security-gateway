package com.ai.gateway.entity;

import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "REQUEST_AUDIT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REQUEST_UUID", nullable = false, unique = true)
    private UUID requestUuid;

    @Lob
    @Column(name = "MASKED_PROMPT", nullable = false)
    private String maskedPrompt;

    @Lob
    @Column(name = "MASKED_RESPONSE")
    private String maskedResponse;

    @Column(name = "LATENCY_MS")
    private Long latencyMs;

    @Column(name = "MODEL_NAME")
    private String modelName;

    @Column(name = "PROVIDER")
    private String provider;

    @Enumerated(EnumType.STRING)
    private AuditStatus status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

}