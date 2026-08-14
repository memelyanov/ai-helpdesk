package com.epam.aihelpdesk.health;

import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Contributes an "azureOpenAi" component to /actuator/health: UP when configuration is complete,
 * UNKNOWN when absent or partial. Performs no network I/O and never returns DOWN — DOWN would push
 * the whole service to 503 for any developer without Azure credentials, which SC-009 forbids
 * (FR-020, FR-021, research Decision 4).
 */
@Component("azureOpenAi")
public class AzureOpenAiConfigHealthIndicator implements HealthIndicator {

    private final AzureOpenAiProperties properties;

    public AzureOpenAiConfigHealthIndicator(AzureOpenAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (properties.isComplete()) {
            return Health.up()
                    .withDetail("configured", true)
                    .withDetail("endpointConfigured", true)
                    .withDetail("chatDeploymentConfigured", true)
                    .build();
        }
        List<String> missing = properties.missing();
        return Health.unknown()
                .withDetail("configured", false)
                .withDetail("missing", missing)
                .build();
    }
}
