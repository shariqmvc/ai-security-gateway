package com.ai.gateway.service.impl;

import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.service.RestoreService;
import com.ai.gateway.service.TokenVaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestoreServiceImpl implements RestoreService {
    private final TokenVaultService tokenVaultService;

    public String restore(String response, UUID requestId) {

        List<TokenVault> tokens =
                tokenVaultService.getTokens(requestId);

        String restored = response;

        for (TokenVault token : tokens) {

            restored = restored.replace(
                    token.getToken(),
                    com.ai.gateway.util.EncryptionUtil.decrypt(
                            token.getEncryptedValue()));
        }

        return restored;
    }
}
