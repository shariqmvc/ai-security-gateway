package com.ai.gateway.tenant;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TENANTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tenantCode;

    @Column(nullable = false)
    private String tenantName;

    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    private TenantType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    private Provider defaultProvider;

    private String defaultModel;

    private LocalDateTime createdAt;

}
