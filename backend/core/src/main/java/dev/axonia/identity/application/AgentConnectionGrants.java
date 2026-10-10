package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.connection.IssuedTokens;
import dev.axonia.identity.api.connection.Pkce;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.identity.application.port.out.AgentConnectionStore;
import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.domain.AgentConnection;
import dev.axonia.identity.domain.AgentConnectionToken;
import dev.axonia.identity.domain.AgentConnectionTokenKind;
import dev.axonia.shared.api.secret.OpaqueSecrets;
import dev.axonia.shared.api.secret.TokenHasher;
import lombok.RequiredArgsConstructor;

/** Issues and exchanges a connection's secrets. Every method runs inside the connection's organization scope. */
@Component
@RequiredArgsConstructor
class AgentConnectionGrants {

    private final AgentConnectionStore connections;
    private final AgentConnectionTokenStore tokens;
    private final ConnectionLifetimes lifetimes;
    private final Clock clock;

    @Transactional
    public String authorize(AuthorizeConnection command) {

        var now = clock.instant();
        var connection = connections.save(AgentConnection.builder().organizationId(command.organizationId())
                .userId(command.userId()).clientId(command.clientId()).clientName(command.clientName())
                .clientHost(command.clientHost()).scopes(ScopeList.join(command.scopes())).resource(command.resource())
                .redirectUri(command.redirectUri()).codeChallenge(command.codeChallenge())
                .endsAt(now.plus(lifetimes.connectionMaxLifetime())).build());
        return issue(connection, AgentConnectionTokenKind.CODE, now.plus(lifetimes.codeTtl()));
    }

    @Transactional
    public Optional<IssuedTokens> redeem(AgentConnectionToken code, RedeemCode command) {

        return connections.findById(code.connectionId())
                .flatMap(connection -> redeem(connection, code, command, clock.instant()));
    }

    @Transactional
    public Optional<IssuedTokens> refresh(AgentConnectionToken presented, RefreshConnection command) {

        return connections.findById(presented.connectionId())
                .flatMap(connection -> refresh(connection, presented, command, clock.instant()));
    }

    private Optional<IssuedTokens> redeem(AgentConnection connection, AgentConnectionToken code, RedeemCode command,
            Instant now) {

        if (tokens.consume(code.id(), now) == 0) {
            revokeIfActive(connection, now);
            return Optional.empty();
        }
        if (!code.isLiveAt(now) || !isRedeemedBy(connection, command)) {
            return Optional.empty();
        }
        connections
                .findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
                        connection.organizationId(), connection.userId(), connection.clientId())
                .forEach(previous -> revokeIfActive(previous, now));
        var active = connections.save(connection.toBuilder().activatedAt(now).build());
        return Optional.of(issuePair(active, now));
    }

    private Optional<IssuedTokens> refresh(AgentConnection connection, AgentConnectionToken presented,
            RefreshConnection command, Instant now) {

        if (!connection.clientId().equals(command.clientId())) {
            return Optional.empty();
        }
        if (tokens.consume(presented.id(), now) == 0) {
            if (isReplayedAfterGrace(presented, now)) {
                revokeIfActive(connection, now);
            }
            return Optional.empty();
        }
        if (!presented.isLiveAt(now) || !connection.isUsableAt(now)) {
            return Optional.empty();
        }
        tokens.deleteAccessTokens(connection.id());
        return Optional.of(issuePair(connection, now));
    }

    private static boolean isRedeemedBy(AgentConnection connection, RedeemCode command) {

        return connection.activatedAt() == null && !connection.isRevoked()
                && connection.clientId().equals(command.clientId())
                && connection.redirectUri().equals(command.redirectUri())
                && Pkce.matches(command.codeVerifier(), connection.codeChallenge());
    }

    /** Two requests racing on one refresh token is a client, not a thief: only a late replay revokes. */
    private boolean isReplayedAfterGrace(AgentConnectionToken presented, Instant now) {

        return presented.consumedAt() != null
                && presented.consumedAt().plus(lifetimes.refreshReuseGrace()).isBefore(now);
    }

    private void revokeIfActive(AgentConnection connection, Instant now) {

        if (connection.activatedAt() != null && !connection.isRevoked()) {
            connections.save(connection.toBuilder().revokedAt(now).build());
        }
    }

    private IssuedTokens issuePair(AgentConnection connection, Instant now) {

        var accessExpiry = earliest(now.plus(lifetimes.accessTokenTtl()), connection.endsAt());
        var access = issue(connection, AgentConnectionTokenKind.ACCESS, accessExpiry);
        var refresh = issue(connection, AgentConnectionTokenKind.REFRESH,
                earliest(now.plus(lifetimes.refreshTokenIdleTtl()), connection.endsAt()));
        return new IssuedTokens(access, refresh, Duration.between(now, accessExpiry),
                ScopeList.parse(connection.scopes()));
    }

    private String issue(AgentConnection connection, AgentConnectionTokenKind kind, Instant expiresAt) {

        var plaintext = OpaqueSecrets.generate(kind.prefix());
        tokens.save(
                AgentConnectionToken.builder().organizationId(connection.organizationId()).connectionId(connection.id())
                        .kind(kind).tokenHash(TokenHasher.sha256Hex(plaintext)).expiresAt(expiresAt).build());
        return plaintext;
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
