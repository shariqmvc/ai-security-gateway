package com.ai.gateway.service.impl;

import com.ai.gateway.personal.billing.PersonalCreditExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayCreditReservationCleanupTest {

    @Mock
    private PersonalCreditExecutionService creditExecutionService;

    @Test
    void releaseFailureDoesNotEscapeAndMaskOriginalStreamingFailure() {
        UUID requestId = UUID.randomUUID();
        PersonalCreditExecutionService.ReservationContext reservation =
                new PersonalCreditExecutionService.ReservationContext(
                        UUID.randomUUID(), new BigDecimal("0.50"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(creditExecutionService).releaseOnFailure(reservation);

        assertDoesNotThrow(() -> GatewayServiceImpl.releaseCreditReservationSafely(
                creditExecutionService, reservation, requestId, "STREAM_FAILURE"));

        verify(creditExecutionService).releaseOnFailure(reservation);
    }

    @Test
    void missingReservationDoesNotAttemptRelease() {
        assertDoesNotThrow(() -> GatewayServiceImpl.releaseCreditReservationSafely(
                creditExecutionService, null, UUID.randomUUID(), "STREAM_FAILURE"));

        verifyNoInteractions(creditExecutionService);
    }
}
