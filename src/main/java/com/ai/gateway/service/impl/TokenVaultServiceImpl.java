package com.ai.gateway.service.impl;

import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.repository.TokenVaultRepository;
import com.ai.gateway.service.TokenVaultService;
import com.ai.gateway.util.EncryptionUtil;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class TokenVaultServiceImpl implements TokenVaultService
{
    private final TokenVaultRepository repository;


    @Override
public void save(UUID requestId, List<DetectedPII> detectedValues) {


    for (DetectedPII pii : detectedValues) {

        TokenVault entity = TokenVault.builder()
                .requestUuid(requestId)
                .token(pii.getToken())
                .encryptedValue(
                        EncryptionUtil.encrypt(
                                pii.getOriginalValue()))
                .piiType(pii.getPiiType())
                .build();

        repository.save(entity);
    }
}

public List<TokenVault> getTokens(UUID requestId) {

    return repository.findByRequestUuid(requestId);
}
    }
