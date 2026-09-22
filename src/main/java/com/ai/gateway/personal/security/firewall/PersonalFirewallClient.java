package com.ai.gateway.personal.security.firewall;

import com.ai.gateway.personal.security.PersonalFirewallProperties;
import com.ai.gateway.personal.security.PersonalFirewallUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for the standalone AIRouter Firewall ML service.
 *
 * <p>No model code is loaded into the Spring Boot process. The client sends
 * only the prompt and correlation id to the local security service.</p>
 */
@Slf4j
@Service
public class PersonalFirewallClient {

    private final PersonalFirewallProperties properties;
    private final RestTemplate restTemplate;

    public PersonalFirewallClient(PersonalFirewallProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())));
        this.restTemplate = new RestTemplate(factory);
    }

    public PersonalFirewallDetection detect(UUID requestId, String text) {
        if (!properties.isEnabled()) {
            return allowDisabled(requestId);
        }

        if (text != null && text.length() > properties.getMaxTextLength()) {
            return new PersonalFirewallDetection(
                    requestId == null ? null : requestId.toString(),
                    "BLOCK",
                    "HIGH",
                    1.0d,
                    List.of("DATA_EXFILTRATION"),
                    List.of("firewall-input-too-large"),
                    "gateway-input-validator",
                    "local",
                    0.0d);
        }

        String url = normalizeBaseUrl(properties.getBaseUrl()) + "/v1/security/detect";
        PersonalFirewallRequest body = new PersonalFirewallRequest(
                requestId == null ? UUID.randomUUID().toString() : requestId.toString(),
                text == null ? "" : text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            PersonalFirewallDetection response = restTemplate.postForObject(
                    url,
                    new HttpEntity<>(body, headers),
                    PersonalFirewallDetection.class);

            if (response == null) {
                return unavailableOrFailOpen("Firewall returned an empty response.", null, requestId);
            }

            return response;
        } catch (RestClientException ex) {
            return unavailableOrFailOpen(
                    "Unable to reach Personal security firewall at " + url + ".",
                    ex,
                    requestId);
        }
    }

    private PersonalFirewallDetection unavailableOrFailOpen(
            String message,
            Throwable cause,
            UUID requestId) {
        if (properties.isFailOpen()) {
            log.warn("Personal firewall unavailable; fail-open is enabled. requestId={}", requestId, cause);
            return new PersonalFirewallDetection(
                    requestId == null ? null : requestId.toString(),
                    "ALLOW",
                    "LOW",
                    0.0d,
                    List.of(),
                    List.of("firewall-unavailable-fail-open"),
                    "firewall-unavailable",
                    "unavailable",
                    0.0d);
        }

        throw new PersonalFirewallUnavailableException(message, cause);
    }

    private PersonalFirewallDetection allowDisabled(UUID requestId) {
        return new PersonalFirewallDetection(
                requestId == null ? null : requestId.toString(),
                "ALLOW",
                "LOW",
                0.0d,
                List.of(),
                List.of("firewall-disabled"),
                "disabled",
                "disabled",
                0.0d);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8090";
        }
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    private record PersonalFirewallRequest(String requestId, String text) {}
}
