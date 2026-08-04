package com.ai.gateway.health.indicators;

import com.ai.gateway.enums.HealthStatus;
import com.ai.gateway.health.HealthIndicator;
import com.ai.gateway.health.HealthResult;
import com.ai.gateway.repository.TokenVaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenVaultHealthIndicator implements HealthIndicator {

    private final TokenVaultRepository repository;

    @Override
    public String name() {
        return "Token Vault";
    }

    @Override
    public HealthResult check() {

        try {

            repository.count();

            return HealthResult.builder()
                    .component(name())
                    .status(HealthStatus.UP)
                    .message("Token Vault is healthy.")
                    .build();

        } catch (Exception ex) {

            return HealthResult.builder()
                    .component(name())
                    .status(HealthStatus.DOWN)
                    .message(ex.getMessage())
                    .build();

        }

    }

}
