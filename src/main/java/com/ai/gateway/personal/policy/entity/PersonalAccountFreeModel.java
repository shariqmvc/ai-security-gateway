package com.ai.gateway.personal.policy.entity;

import com.ai.gateway.personal.entity.PersonalAccount;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "PERSONAL_ACCOUNT_FREE_MODELS",
       uniqueConstraints = @UniqueConstraint(columnNames = {"personal_account_id", "model_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccountFreeModel {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_account_id", nullable = false)
    private PersonalAccount personalAccount;

    @Column(name = "model_key", nullable = false, length = 160)
    private String modelKey;
}
