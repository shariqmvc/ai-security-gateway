package com.ai.gateway.cost.entity;

import com.ai.gateway.enums.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "REQUEST_COST")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestCost {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "input_cost", precision = 19, scale = 8)
    private BigDecimal inputCost;

    @Column(name = "output_cost", precision = 19, scale = 8)
    private BigDecimal outputCost;

    @Column(name = "total_cost", precision = 19, scale = 8)
    private BigDecimal totalCost;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}