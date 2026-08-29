package com.ai.gateway.core.failover;

import com.ai.gateway.config.ProviderRequestBudgetExceededException;
import com.ai.gateway.core.multimodal.MediaInputException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProviderFailureClassifierTest {

    @Test
    void classifiesSocketTimeoutAsTimeout() {
        ResourceAccessException failure =
                new ResourceAccessException(
                        "I/O error on request",
                        new SocketTimeoutException("Read timed out"));

        assertEquals(
                ProviderFailureCategory.TIMEOUT,
                ProviderFailureClassifier.classify(failure));
        assertTrue(ProviderFailureClassifier.isRetryable(failure));
    }

    @Test
    void classifiesNetworkFailureSeparately() {
        ResourceAccessException failure =
                new ResourceAccessException("Connection refused");

        assertEquals(
                ProviderFailureCategory.NETWORK,
                ProviderFailureClassifier.classify(failure));
    }

    @Test
    void honorsRetryAfterHeaderForRateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "12");

        HttpClientErrorException failure =
                HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        headers,
                        null,
                        null);

        assertEquals(
                ProviderFailureCategory.RATE_LIMITED,
                ProviderFailureClassifier.classify(failure));

        assertEquals(
                Duration.ofSeconds(12),
                ProviderFailureClassifier.retryAfter(failure).orElseThrow());
    }

    @Test
    void classifiesBadRequestAsNonRetryableClientError() {
        HttpClientErrorException failure =
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        null,
                        null);

        assertEquals(
                ProviderFailureCategory.CLIENT_ERROR,
                ProviderFailureClassifier.classify(failure));
        assertFalse(ProviderFailureClassifier.isRetryable(failure));
    }

    @Test
    void classifiesMediaInputFailureAsNonRetryable() {
        MediaInputException failure =
                new MediaInputException("Unable to fetch media URL: HTTP 403.");

        assertEquals(
                ProviderFailureCategory.MEDIA_INPUT,
                ProviderFailureClassifier.classify(failure));

        assertFalse(ProviderFailureClassifier.isRetryable(failure));
    }

    @Test
    void classifiesRequestBudgetExhaustionAsNonRetryable() {
        ProviderRequestBudgetExceededException failure =
                new ProviderRequestBudgetExceededException("budget exhausted");

        assertEquals(
                ProviderFailureCategory.REQUEST_BUDGET_EXHAUSTED,
                ProviderFailureClassifier.classify(failure));

        assertFalse(ProviderFailureClassifier.isRetryable(failure));
    }

}
