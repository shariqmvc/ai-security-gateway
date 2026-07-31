package com.ai.gateway.service.impl;

import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.service.RestoreService;
import com.ai.gateway.service.TokenVaultService;
import com.ai.gateway.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class RestoreServiceImpl implements RestoreService {

    private final TokenVaultService tokenVaultService;
    private final EncryptionUtil encryptionUtil;

    public String restore(String response, UUID requestId) {

        List<TokenVault> tokens =
                tokenVaultService.getTokens(requestId);

        Map<String, String> tokenMap = new HashMap<>();

        for (TokenVault token : tokens) {

            tokenMap.put(
                    token.getToken(),
                    encryptionUtil.decrypt(
                            token.getEncryptedValue()));
        }

        String restored = response;

        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {

            restored = restored.replace(
                    entry.getKey(),
                    entry.getValue());
        }
        log.debug(
                "Response restored. requestId={}",
                requestId);
        return restored;
    }

}
