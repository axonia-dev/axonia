package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.identity.domain.AgentConnection;

/** Agents' connections to members' accounts. */
public interface AgentConnectionStore {

    Optional<AgentConnection> findById(Long id);

    /** One member's activated connections, most recently activated first. */
    List<AgentConnection> findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(
            String organizationId, String userId);

    Optional<AgentConnection> findByOrganizationIdAndUserIdAndId(String organizationId, String userId, Long id);

    /** The connections one client holds active for one member. */
    List<AgentConnection> findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
            String organizationId, String userId, String clientId);

    AgentConnection save(AgentConnection connection);

    /** Writes at most once per {@code staleBefore} window. */
    void touchLastUsed(String organizationId, Long id, Instant now, Instant staleBefore);

    /**
     * Deletes connections never activated before {@code pendingBefore}, and those revoked or ended before
     * {@code retiredBefore}.
     */
    int deleteRetired(String organizationId, Instant pendingBefore, Instant retiredBefore);
}
