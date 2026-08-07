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

    @Column(name = "request_id")
    private UUID requestId;


    @Enumerated(EnumType.STRING)
    private Provider provider;

    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "reasoning_tokens")
    private Integer reasoningTokens;

    private LocalDateTime createdAt;






}
