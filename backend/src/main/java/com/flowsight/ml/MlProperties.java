package com.flowsight.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "application.ml")
public record MlProperties(
    String url,
    String token,
    @DefaultValue("250ms") Duration connectTimeout,
    @DefaultValue("5") int failureThreshold,
    @DefaultValue("30s") Duration openDuration,
    Map<String, CapabilityFlags> capabilities
) {

    /**
     * enabled: serve the model's answer (rules on any failure).
     * shadow: when not enabled, still call the model in the background and log it, but serve rules.
     */
    public record CapabilityFlags(boolean enabled, boolean shadow, @DefaultValue("400ms") Duration timeout) {
    }

    private static final CapabilityFlags OFF = new CapabilityFlags(false, false, Duration.ofMillis(400));

    public boolean configured() {
        return url != null && !url.isBlank() && token != null && !token.isBlank();
    }

    public CapabilityFlags flags(MlCapability capability) {
        return capabilities == null ? OFF : capabilities.getOrDefault(capability.id(), OFF);
    }
}
