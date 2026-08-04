package com.ai.gateway.repository;

import com.ai.gateway.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TokenUsageRepository
        extends JpaRepository<TokenUsage, UUID> {
}
