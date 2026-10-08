package dev.axonia.communication.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The cadences of the two cluster-wide communication tasks {@link CommunicationTasks} contributes. Retention's first
 * run has its own short delay: tied to its 24-hour interval, an instance restarted inside a day would never reach it.
 */
@ConfigurationProperties(prefix = "axonia.communication")
record CommunicationSchedule(@DefaultValue("5m") Duration produceInterval,
        @DefaultValue("24h") Duration retentionInterval, @DefaultValue("10m") Duration retentionInitialDelay) {
}
