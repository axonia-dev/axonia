package dev.axonia.identity.internal;

import org.springframework.stereotype.Component;

import dev.axonia.OrganizationIds;
import dev.axonia.OrganizationScope;
import dev.axonia.SweepHeartbeat;
import dev.axonia.SweepHeartbeat.Sweep;
import dev.axonia.identity.connection.AgentConnections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes expired MCP codes and tokens and long-retired connections, one organization scope at a time.
 * {@link AgentConnectionTasks} runs {@link #tick} once across the cluster.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class AgentConnectionRetentionSweep {

    private final OrganizationIds organizationIds;
    private final AgentConnections connections;
    private final SweepHeartbeat heartbeat;

    void tick() {

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
