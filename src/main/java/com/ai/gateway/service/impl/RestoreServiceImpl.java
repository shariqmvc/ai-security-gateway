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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestoreServiceImpl implements RestoreService {

    private final TokenVaultService tokenVaultService;
    private final EncryptionUtil encryptionUtil;

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("<PII_[A-Z]+_[^>]+>");


    @Override
    public String restore(String response, UUID requestId) {
            List<TokenVault> vaultEntries =
                    tokenVaultService.getTokens(requestId);

            if (vaultEntries.isEmpty()) {
                return response;
            }

            Map<String, String> tokenMap = new HashMap<>();

            for (TokenVault vault : vaultEntries) {

                tokenMap.put(
                        vault.getToken(),
                        encryptionUtil.decrypt(
                                vault.getEncryptedValue()
                        )
                );
            }

            Matcher matcher = TOKEN_PATTERN.matcher(response);

            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {

                String token = matcher.group();

                matcher.appendReplacement(
                        sb,
                        Matcher.quoteReplacement(
                                tokenMap.getOrDefault(token, token)
                        )
                );
            }

            matcher.appendTail(sb);

            return sb.toString();
        }
}
