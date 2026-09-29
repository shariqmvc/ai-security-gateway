package com.ai.gateway.personal.policy.entity;

import com.ai.gateway.personal.entity.PersonalAccount;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "PERSONAL_ACCOUNT_FEATURES",
       uniqueConstraints = @UniqueConstraint(columnNames = {"personal_account_id", "feature"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccountFeature {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_account_id", nullable = false)
    private PersonalAccount personalAccount;

    @Column(name = "feature", nullable = false, length = 64)
    private String feature;
}
