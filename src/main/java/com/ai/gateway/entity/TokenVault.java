package com.ai.gateway.entity;

import com.ai.gateway.constants.PIIType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TOKEN_VAULT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REQUEST_UUID", nullable = false)
    private UUID requestUuid;

    @Column(name = "TOKEN", nullable = false, unique = true, length = 100)
    private String token;

    @Lob
    @Column(name = "ENCRYPTED_VALUE", nullable = false)
    private String encryptedValue;

    @Enumerated(EnumType.STRING)
    private PIIType piiType;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

}