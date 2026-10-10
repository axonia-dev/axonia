package dev.axonia.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.connection.ConnectionPrincipal;
import dev.axonia.identity.api.connection.ConnectionSummary;
import dev.axonia.identity.api.connection.IssuedTokens;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.identity.domain.AgentConnectionTokenKind;
import dev.axonia.shared.api.secret.TokenHasher;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A token is found by its digest before any organization is known, then everything else about it runs inside that
 * token's own organization scope, so row-level security holds for the token endpoint and the /mcp filter alike.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AgentConnectionAdapter implements AgentConnections {

    private final AgentConnectionTokenStore tokens;
    private final AgentConnectionGrants grants;
    private final AgentConnectionLedger ledger;
    private final Clock clock;

    @Override
    public String authorize(AuthorizeConnection command) {
        return grants.authorize(command);
    }

    @Override
    public Optional<IssuedTokens> redeem(RedeemCode command) {

        return find(command.code(), AgentConnectionTokenKind.CODE).flatMap(code -> refusedOnRace(
                () -> OrganizationScope.runAs(code.organizationId(), () -> grants.redeem(code, command))));
    }

    @Override
    public Optional<IssuedTokens> refresh(RefreshConnection command) {

        return find(command.refreshToken(), AgentConnectionTokenKind.REFRESH).flatMap(token -> refusedOnRace(
                () -> OrganizationScope.runAs(token.organizationId(), () -> grants.refresh(token, command))));
    }

    @Override
    public Optional<ConnectionPrincipal> authenticate(String accessToken) {

        var now = clock.instant();
        return find(accessToken, AgentConnectionTokenKind.ACCESS).filter(access -> access.isLiveAt(now))
                .flatMap(access -> OrganizationScope.runAs(access.organizationId(), () -> ledger.principal(access)));
    }

    @Override
    public void revokeToken(String token) {

        AgentConnectionTokenKind.ofToken(token).flatMap(kind -> find(token, kind)).ifPresent(found -> OrganizationScope
                .runAs(found.organizationId(), () -> ledger.revokeConnection(found.connectionId())));
    }

    @Override
    public List<ConnectionSummary> list(String userId, String organizationId) {
        return ledger.list(userId, organizationId);
    }

    @Override
    public void revoke(String userId, String organizationId, Long connectionId) {
        ledger.revoke(userId, organizationId, connectionId);
    }

    @Override
    public void recordUsage(String organizationId, Long connectionId) {
        ledger.recordUsage(organizationId, connectionId);
    }

    @Override
    public int sweep(String organizationId) {
        return ledger.sweep(organizationId);
    }

    /**
     * A grant racing another redemption, a revocation or the sweep loses at the unique index, the version check or a
     * foreign key. Its transaction has rolled back, and the client is told invalid_grant like any other refusal.
     */
    private static Optional<IssuedTokens> refusedOnRace(Supplier<Optional<IssuedTokens>> grant) {

        try {
            return grant.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.info("Grant refused after losing a race: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<AgentConnectionToken> find(String plaintext, AgentConnectionTokenKind kind) {

        if (plaintext == null || !plaintext.startsWith(kind.prefix())) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(TokenHasher.sha256Hex(plaintext)).filter(token -> token.kind() == kind);
    }
}
