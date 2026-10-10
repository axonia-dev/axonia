package dev.axonia.identity.adapter.in.schedule;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.configuration.AgentConnectionTasks;
import dev.axonia.organization.api.OrganizationIds;
import dev.axonia.shared.api.schedule.SweepHeartbeat;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes expired MCP codes and tokens and long-retired connections, one organization scope at a time.
 * {@link AgentConnectionTasks} runs {@link #tick} once across the cluster.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentConnectionRetentionSweep {

    private final OrganizationIds organizationIds;
    private final AgentConnections connections;
    private final SweepHeartbeat heartbeat;

    public void tick() {

        heartbeat.record(Sweep.AGENT_CONNECTION_RETENTION, () -> {
            for (var organizationId : organizationIds.all()) {
                try {
                    OrganizationScope.runAs(organizationId, () -> connections.sweep(organizationId));
                } catch (RuntimeException e) {
                    log.warn("Agent connection retention failed for organization {}: {}", organizationId,
                            e.getClass().getSimpleName());
                }
            }
        });
    }
}
