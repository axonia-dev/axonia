package dev.axonia.communication.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.communication.adapter.in.schedule.CommunicationSweep;
import dev.axonia.shared.api.schedule.ClusteredTask;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;

/**
 * Producing and retention run once across the cluster: two instances producing at once would enqueue the same wrap-up
 * twice. Draining stays on {@code @Scheduled} in {@link CommunicationSweep}, because every instance drains its own
 * claimed batch.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "axonia.communication.sweep", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(CommunicationSchedule.class)
public class CommunicationTasks {

    @Bean
    ClusteredTask communicationProduceTask(CommunicationSweep sweep, CommunicationSchedule schedule) {

        return new ClusteredTask(Sweep.COMMUNICATION_PRODUCE.tag(),
                new ClusteredTask.Every(schedule.produceInterval(), schedule.produceInterval()), sweep::produceTick);
    }

    @Bean
    ClusteredTask communicationRetentionTask(CommunicationSweep sweep, CommunicationSchedule schedule) {

        return new ClusteredTask(Sweep.COMMUNICATION_RETENTION.tag(),
                new ClusteredTask.Every(schedule.retentionInterval(), schedule.retentionInitialDelay()),
                sweep::retentionTick);
    }
}
