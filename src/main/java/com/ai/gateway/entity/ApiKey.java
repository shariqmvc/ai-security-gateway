package com.ai.gateway.entity;

import com.ai.gateway.enums.ApiKeyStatus;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.Tenant;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    private ApiKeyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private SecurityRole role = SecurityRole.TENANT_USER;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

}
