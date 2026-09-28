package com.ai.gateway.personal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_PHONE_VERIFICATION_CODES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalPhoneVerificationCode {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private PersonalUser user;
    @Column(name = "code_hash", nullable = false, length = 64) private String codeHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "attempts", nullable = false) @Builder.Default private int attempts = 0;
}