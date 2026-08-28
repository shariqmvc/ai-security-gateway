package com.ai.gateway.failover;

import com.ai.gateway.config.ProviderRequestBudgetExceededException;
import com.ai.gateway.multimodal.MediaInputException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import java.util.Locale;

/**
 * Converts provider exceptions into stable routing/failover categories.
 *
 * The classifier deliberately distinguishes network failures, timeouts,
 * rate limits and server failures because they have different recovery
 * characteristics and therefore different circuit-open durations.
 */
public final class ProviderFailureClassifier {

    private ProviderFailureClassifier() {
    }

    public static ProviderFailureCategory classify(Throwable failure) {
        Throwable current = failure;

        while (current != null) {
            if (current instanceof ProviderRequestBudgetExceededException) {
                return ProviderFailureCategory.REQUEST_BUDGET_EXHAUSTED;
            }

            if (current instanceof MediaInputException) {
                return ProviderFailureCategory.MEDIA_INPUT;
            }

            if (current instanceof SocketTimeoutException) {
                return ProviderFailureCategory.TIMEOUT;
            }

            if (current instanceof ResourceAccessException) {
                return isTimeout(current)
                        ? ProviderFailureCategory.TIMEOUT
                        : ProviderFailureCategory.NETWORK;
            }

            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();

                if (status == 408) {
                    return ProviderFailureCategory.TIMEOUT;
                }
                if (status == 429) {
                    return ProviderFailureCategory.RATE_LIMITED;
                }
                if (status >= 500) {
                    return ProviderFailureCategory.SERVER_ERROR;
                }
                if (status >= 400) {
                    return ProviderFailureCategory.CLIENT_ERROR;
                }
            }

            current = current.getCause();
        }

        return ProviderFailureCategory.UNKNOWN;
    }

    public static boolean isRetryable(Throwable failure) {
        ProviderFailureCategory category = classify(failure);
        return category == ProviderFailureCategory.NETWORK
                || category == ProviderFailureCategory.TIMEOUT
                || category == ProviderFailureCategory.RATE_LIMITED
                || category == ProviderFailureCategory.SERVER_ERROR
                || category == ProviderFailureCategory.UNKNOWN;
    }



    /**
     * Returns a server-provided Retry-After duration when available for a
     * rate-limited response. A missing or malformed header returns empty.
     */
    public static java.util.Optional<java.time.Duration> retryAfter(Throwable failure) {
        Throwable current = failure;

        while (current != null) {
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == 429) {

                String header =
                        responseException.getResponseHeaders() == null
                                ? null
                                : responseException.getResponseHeaders()
                                .getFirst("Retry-After");

                if (header == null || header.isBlank()) {
                    return java.util.Optional.empty();
                }

                try {
                    long seconds = Long.parseLong(header.trim());
                    if (seconds >= 0) {
                        return java.util.Optional.of(java.time.Duration.ofSeconds(seconds));
                    }
                } catch (NumberFormatException ignored) {
                    try {
                        java.time.Instant retryAt =
                                ZonedDateTime.parse(header.trim()).toInstant();
                        java.time.Duration duration =
                                java.time.Duration.between(
                                        java.time.Instant.now(),
                                        retryAt);
                        return java.util.Optional.of(
                                duration.isNegative()
                                        ? java.time.Duration.ZERO
                                        : duration);
                    } catch (DateTimeParseException ignoredDate) {
                        return java.util.Optional.empty();
                    }
                }
            }

            current = current.getCause();
        }

        return java.util.Optional.empty();
    }

    private static boolean isTimeout(Throwable failure) {
        Throwable current = failure;

        while (current != null) {
            String message = current.getMessage();

            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);

                if (normalized.contains("timed out")
                        || normalized.contains("timeout")
                        || normalized.contains("read timed out")
                        || normalized.contains("connect timed out")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }
}
