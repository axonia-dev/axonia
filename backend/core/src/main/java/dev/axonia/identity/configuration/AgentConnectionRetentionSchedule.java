package dev.axonia.identity.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "axonia.identity.agent-connection-retention")
record AgentConnectionRetentionSchedule(@DefaultValue("24h") Duration interval,
        @DefaultValue("20m") Duration initialDelay) {
}
