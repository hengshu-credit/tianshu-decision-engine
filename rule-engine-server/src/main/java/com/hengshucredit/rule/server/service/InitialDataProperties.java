package com.hengshucredit.rule.server.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional startup seed scripts.  The scripts are deliberately separate from
 * the structural schema so operators can enable the sample/built-in catalog
 * without making it part of every database initialization.
 */
@ConfigurationProperties(prefix = "rule-engine.initial-data")
public class InitialDataProperties {

    /** Whether configured seed scripts should run during server startup. */
    private boolean enabled;

    /** Classpath or filesystem resources containing idempotent SQL statements. */
    private List<String> locations = new ArrayList<>(List.of("classpath:sql/initial-data.sql"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations == null ? new ArrayList<>() : new ArrayList<>(locations);
    }
}
