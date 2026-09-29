package com.ai.gateway.personal.preferences.entity;

import com.ai.gateway.personal.entity.PersonalAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_ACCOUNT_PREFERENCES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccountPreferences {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_account_id", nullable = false, unique = true)
    private PersonalAccount personalAccount;

    @Column(name = "default_provider", length = 32)
    private String defaultProvider;

    @Column(name = "default_model", length = 160)
    private String defaultModel;

    @Column(name = "billing_mode", nullable = false, length = 16)
    @Builder.Default
    private String billingMode = "AUTO";

    @Column(name = "routing_priority", nullable = false, length = 32)
    @Builder.Default
    private String routingPriority = "BALANCED";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
