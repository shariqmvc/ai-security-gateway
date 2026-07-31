package com.ai.gateway.service.impl;

import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.repository.TokenVaultRepository;
import com.ai.gateway.service.TokenVaultService;
import com.ai.gateway.util.EncryptionUtil;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenVaultServiceImpl implements TokenVaultService
{
    private final TokenVaultRepository repository;
    private final EncryptionUtil encryptionUtil;

    @Override
public void save(UUID requestId, List<DetectedPII> detectedValues) {

        List<TokenVault> entities = new ArrayList<>();

        for (DetectedPII pii : detectedValues) {

            entities.add(
                    TokenVault.builder()
                            .requestUuid(requestId)
                            .token(pii.getToken())
                            .encryptedValue(
                                    encryptionUtil.encrypt(pii.getOriginalValue()))
                            .piiType(pii.getPiiType())
                            .build());
        }

        repository.saveAll(entities);
}

public List<TokenVault> getTokens(UUID requestId) {

    return repository.findByRequestUuid(requestId);
}
    }
