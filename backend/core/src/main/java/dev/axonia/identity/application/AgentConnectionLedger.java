package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.connection.ConnectionPrincipal;
import dev.axonia.identity.api.connection.ConnectionSummary;
import dev.axonia.identity.application.port.out.AgentConnectionStore;
import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.domain.AgentConnection;
import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Reads, revokes and retires connections. Every method runs inside the connection's organization scope. */
@Slf4j
@Component
@RequiredArgsConstructor
class AgentConnectionLedger {

    private static final Duration PENDING_RETENTION = Duration.ofDays(1);
    private static final Duration USAGE_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final AgentConnectionStore connections;
    private final AgentConnectionTokenStore tokens;
    private final ConnectionLifetimes lifetimes;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<ConnectionPrincipal> principal(AgentConnectionToken access) {

        var now = clock.instant();
        return connections.findById(access.connectionId()).filter(connection -> connection.isUsableAt(now))
                .map(connection -> new ConnectionPrincipal(connection.userId(), connection.organizationId(),
                        ScopeList.parse(connection.scopes()), connection.id(), connection.clientId()));
    }

    @Transactional(readOnly = true)
    public List<ConnectionSummary> list(String userId, String organizationId) {

        var now = clock.instant();
        return connections
                .findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(organizationId, userId)
                .stream().map(c -> new ConnectionSummary(c.id(), c.clientName(), c.clientHost(),
                        ScopeList.parse(c.scopes()), c.activatedAt(), c.lastUsedAt(), c.endsAt(), c.statusAt(now)))
                .toList();
    }

    @Transactional
    public void revoke(String userId, String organizationId, Long connectionId) {

        var connection = connections.findByOrganizationIdAndUserIdAndId(organizationId, userId, connectionId)
                .orElseThrow(() -> new NotFoundException("Agent connection not found: " + connectionId));
        revokeIfLive(connection);
    }

    @Transactional
    public void revokeConnection(Long connectionId) {
        connections.findById(connectionId).ifPresent(this::revokeIfLive);
    }

    /** One UPDATE needs no transaction, and outside one a failed write cannot abort anything it is swallowed from. */
    public void recordUsage(String organizationId, Long connectionId) {

        var now = clock.instant();
        try {
            connections.touchLastUsed(organizationId, connectionId, now, now.minus(USAGE_WRITE_INTERVAL));
        } catch (RuntimeException e) {
            log.warn("Failed to record agent connection usage for organization {} connection {}", organizationId,
                    connectionId, e);
        }
    }

    @Transactional
    public int sweep(String organizationId) {

        var now = clock.instant();
        return tokens.deleteExpired(organizationId, now) + connections.deleteRetired(organizationId,
                now.minus(PENDING_RETENTION), now.minus(lifetimes.retiredRetention()));
    }

    private void revokeIfLive(AgentConnection connection) {

        if (!connection.isRevoked()) {
            connections.save(connection.toBuilder().revokedAt(clock.instant()).build());
        }
    }
}
