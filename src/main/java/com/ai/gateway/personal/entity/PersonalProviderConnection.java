package com.ai.gateway.personal.entity;

import com.ai.gateway.core.model.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "PERSONAL_PROVIDER_CONNECTIONS",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_provider_account_provider",
                columnNames = {"personal_account_id", "provider"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalProviderConnection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_account_id", nullable = false)
    private PersonalAccount personalAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "encrypted_api_key", nullable = false, length = 4096)
    private String encryptedApiKey;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    @Column(name = "validation_message", length = 500)
    private String validationMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
