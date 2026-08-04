package com.ai.gateway.health.indicators;

import com.ai.gateway.enums.HealthStatus;
import com.ai.gateway.health.HealthIndicator;
import com.ai.gateway.health.HealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public String name() {
        return "Database";
    }

    @Override
    public HealthResult check() {

        try (Connection connection = dataSource.getConnection()) {

            if (connection.isValid(2)) {

                return HealthResult.builder()
                        .component(name())
                        .status(HealthStatus.UP)
                        .message("Database connection is healthy.")
                        .build();

            }

            return HealthResult.builder()
                    .component(name())
                    .status(HealthStatus.DEGRADED)
                    .message("Database connection is not valid.")
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
