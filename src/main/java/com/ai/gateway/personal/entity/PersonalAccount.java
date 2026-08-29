package com.ai.gateway.personal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_ACCOUNTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private PersonalUser user;

    @Column(name = "plan", nullable = false, length = 32)
    @Builder.Default
    private String plan = "PERSONAL_FREE";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
