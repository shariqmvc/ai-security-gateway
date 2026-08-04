package com.ai.gateway.entity;

import com.ai.gateway.enums.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TOKEN_USAGE")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID requestId;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private LocalDateTime createdAt;

}
