package com.ai.gateway.personal.inference;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.service.TokenVaultService;
import com.ai.gateway.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Account-scoped PII token storage for AIRouter Personal.
 *
 * This deliberately uses the public/control-plane Personal schema rather than
 * TenantContext/TenantSchemaRoutingService. Personal accounts are not tenants.
 */
@Service
@RequiredArgsConstructor
public class PersonalTokenVaultService {

    private final JdbcTemplate jdbc;
    private final EncryptionUtil encryptionUtil;

    @Transactional
    public void save(AuthenticationContext auth, UUID requestId, List<DetectedPII> detectedValues) {
        if (auth == null || !auth.isPersonalPrincipal() || auth.getPersonalAccountId() == null
                || requestId == null || detectedValues == null || detectedValues.isEmpty()) {
            return;
        }

        List<Object[]> rows = new ArrayList<>();
        for (DetectedPII pii : detectedValues) {
            if (pii == null || pii.getToken() == null || pii.getOriginalValue() == null || pii.getPiiType() == null) {
                continue;
            }
            rows.add(new Object[]{
                    UUID.randomUUID(),
                    auth.getPersonalAccountId(),
                    requestId,
                    pii.getToken(),
                    encryptionUtil.encrypt(pii.getOriginalValue()),
                    pii.getPiiType().name()
            });
        }

        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO PERSONAL_TOKEN_VAULT
                    (id, personal_account_id, request_uuid, token, encrypted_value, pii_type, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (request_uuid, token) DO NOTHING
                    """, rows);
        }
    }

    @Transactional(readOnly = true)
    public List<PersonalToken> getTokens(UUID requestId) {
        if (requestId == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT token, encrypted_value
                  FROM PERSONAL_TOKEN_VAULT
                 WHERE request_uuid = ?
                 ORDER BY created_at
                """,
                (rs, rowNum) -> new PersonalToken(
                        rs.getString("token"),
                        rs.getString("encrypted_value")
                ),
                requestId);
    }

    public record PersonalToken(String token, String encryptedValue) {}
}
