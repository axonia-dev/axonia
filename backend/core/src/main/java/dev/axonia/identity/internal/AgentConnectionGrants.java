package dev.axonia.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.OpaqueSecrets;
import dev.axonia.TokenHasher;
import dev.axonia.identity.connection.AuthorizeConnection;
import dev.axonia.identity.connection.IssuedTokens;
import dev.axonia.identity.connection.Pkce;
import dev.axonia.identity.connection.RedeemCode;
import dev.axonia.identity.connection.RefreshConnection;
import lombok.RequiredArgsConstructor;

/** Issues and exchanges a connection's secrets. Every method runs inside the connection's organization scope. */
@Component
@RequiredArgsConstructor
class AgentConnectionGrants {

    private final AgentConnectionRepository connections;
    private final AgentConnectionTokenRepository tokens;
    private final AgentConnectionLifetimes lifetimes;
    private final Clock clock;

    @Transactional
    public String authorize(AuthorizeConnection command) {

        var now = clock.instant();
        var connection = connections.save(AgentConnectionEntity.builder().organizationId(command.organizationId())
                .userId(command.userId()).clientId(command.clientId()).clientName(command.clientName())
                .clientHost(command.clientHost()).scopes(ScopeList.join(command.scopes())).resource(command.resource())
                .redirectUri(command.redirectUri()).codeChallenge(command.codeChallenge())
                .endsAt(now.plus(lifetimes.connectionMaxLifetime())).build());
        return issue(connection, AgentConnectionTokenKind.CODE, now.plus(lifetimes.codeTtl()));
    }

    @Transactional
    public Optional<IssuedTokens> redeem(AgentConnectionTokenEntity code, RedeemCode command) {

        return connections.findById(code.getConnectionId())
                .flatMap(connection -> redeem(connection, code, command, clock.instant()));
    }

    @Transactional
    public Optional<IssuedTokens> refresh(AgentConnectionTokenEntity presented, RefreshConnection command) {

        return connections.findById(presented.getConnectionId())
                .flatMap(connection -> refresh(connection, presented, command, clock.instant()));
    }

    private Optional<IssuedTokens> redeem(AgentConnectionEntity connection, AgentConnectionTokenEntity code,
            RedeemCode command, Instant now) {

        if (tokens.consume(code.getId(), now) == 0) {
            revokeIfActive(connection, now);
            return Optional.empty();
        }
        if (!code.isLiveAt(now) || !isRedeemedBy(connection, command)) {
            return Optional.empty();
        }
        connections
                .findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
                        connection.getOrganizationId(), connection.getUserId(), connection.getClientId())
                .forEach(previous -> revokeIfActive(previous, now));
        var active = connections.save(connection.toBuilder().activatedAt(now).build());
        return Optional.of(issuePair(active, now));
    }

    private Optional<IssuedTokens> refresh(AgentConnectionEntity connection, AgentConnectionTokenEntity presented,
            RefreshConnection command, Instant now) {

        if (!connection.getClientId().equals(command.clientId())) {
            return Optional.empty();
        }
        if (tokens.consume(presented.getId(), now) == 0) {
            if (isReplayedAfterGrace(presented, now)) {
                revokeIfActive(connection, now);
            }
            return Optional.empty();
        }
        if (!presented.isLiveAt(now) || !connection.isUsableAt(now)) {
            return Optional.empty();
        }
        tokens.deleteAccessTokens(connection.getId());
        return Optional.of(issuePair(connection, now));
    }

    private static boolean isRedeemedBy(AgentConnectionEntity connection, RedeemCode command) {

        return connection.getActivatedAt() == null && !connection.isRevoked()
                && connection.getClientId().equals(command.clientId())
                && connection.getRedirectUri().equals(command.redirectUri())
                && Pkce.matches(command.codeVerifier(), connection.getCodeChallenge());
    }

    /** Two requests racing on one refresh token is a client, not a thief: only a late replay revokes. */
    private boolean isReplayedAfterGrace(AgentConnectionTokenEntity presented, Instant now) {

        return presented.getConsumedAt() != null
                && presented.getConsumedAt().plus(lifetimes.refreshReuseGrace()).isBefore(now);
    }

    private void revokeIfActive(AgentConnectionEntity connection, Instant now) {

        if (connection.getActivatedAt() != null && !connection.isRevoked()) {
            connections.save(connection.toBuilder().revokedAt(now).build());
        }
    }

    private IssuedTokens issuePair(AgentConnectionEntity connection, Instant now) {

        var accessExpiry = earliest(now.plus(lifetimes.accessTokenTtl()), connection.getEndsAt());
        var access = issue(connection, AgentConnectionTokenKind.ACCESS, accessExpiry);
        var refresh = issue(connection, AgentConnectionTokenKind.REFRESH,
                earliest(now.plus(lifetimes.refreshTokenIdleTtl()), connection.getEndsAt()));
        return new IssuedTokens(access, refresh, Duration.between(now, accessExpiry),
                ScopeList.parse(connection.getScopes()));
    }

    private String issue(AgentConnectionEntity connection, AgentConnectionTokenKind kind, Instant expiresAt) {

        var plaintext = OpaqueSecrets.generate(kind.prefix());
        tokens.save(AgentConnectionTokenEntity.builder().organizationId(connection.getOrganizationId())
                .connectionId(connection.getId()).kind(kind).tokenHash(TokenHasher.sha256Hex(plaintext))
                .expiresAt(expiresAt).build());
        return plaintext;
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
