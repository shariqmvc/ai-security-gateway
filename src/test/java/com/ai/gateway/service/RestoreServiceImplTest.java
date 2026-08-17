package com.ai.gateway.service;

import com.ai.gateway.service.impl.RestoreServiceImpl;
import com.ai.gateway.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestoreServiceImplTest {

    @Mock
    private TokenVaultService tokenVaultService;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Test
    void skipsTenantDatabaseLookupWhenResponseHasNoPiiToken() {
        RestoreServiceImpl service =
                new RestoreServiceImpl(tokenVaultService, encryptionUtil);

        String response = "Hello from Gemini.";

        assertEquals(
                response,
                service.restore(response, UUID.randomUUID()));

        verifyNoInteractions(tokenVaultService, encryptionUtil);
    }
}
