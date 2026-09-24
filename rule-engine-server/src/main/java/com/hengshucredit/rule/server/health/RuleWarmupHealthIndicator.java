package com.hengshucredit.rule.server.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("ruleWarmup")
public class RuleWarmupHealthIndicator implements HealthIndicator {
    private final RuleWarmupStatus status;

    public RuleWarmupHealthIndicator(RuleWarmupStatus status) {
        this.status = status;
    }

    @Override
    public Health health() {
        Map<String, Object> details = status.details();
        Health.Builder builder = status.getState() == RuleWarmupState.READY
                ? Health.up() : Health.outOfService();
        return builder.withDetails(details).build();
    }
}
