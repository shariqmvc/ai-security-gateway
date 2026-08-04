package com.ai.gateway.entity;

import com.ai.gateway.enums.ApiKeyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "API_KEYS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    private ApiKeyStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

}
