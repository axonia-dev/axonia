package dev.axonia.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.axonia.ClusteredTask;
import dev.axonia.SweepHeartbeat.Sweep;

/** {@link AgentConnectionRetentionSweep} runs once across the cluster. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentConnectionRetentionSchedule.class)
class AgentConnectionTasks {

    @Bean
    ClusteredTask agentConnectionRetentionTask(AgentConnectionRetentionSweep sweep,
            AgentConnectionRetentionSchedule schedule) {

        return new ClusteredTask(Sweep.AGENT_CONNECTION_RETENTION.tag(),
                new ClusteredTask.Every(schedule.interval(), schedule.initialDelay()), sweep::tick);
    }
}
