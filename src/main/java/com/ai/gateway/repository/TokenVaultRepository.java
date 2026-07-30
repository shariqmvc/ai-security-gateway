package com.ai.gateway.repository;

import com.ai.gateway.entity.TokenVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenVaultRepository extends JpaRepository<TokenVault, Long> {

    List<TokenVault> findByRequestUuid(UUID requestUuid);

    Optional<TokenVault> findByRequestUuidAndToken(UUID requestUuid, String token);

    boolean existsByToken(String token);

    void deleteByRequestUuid(UUID requestUuid);

}