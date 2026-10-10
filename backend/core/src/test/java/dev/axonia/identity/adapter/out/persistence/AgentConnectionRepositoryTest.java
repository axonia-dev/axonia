package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.domain.AgentConnectionTokenKind;
import dev.axonia.shared.api.secret.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConnectionRepositoryTest extends CoreJdbcTestSupport {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    AgentConnectionRepository connections;

    @Autowired
    AgentConnectionTokenRepository tokens;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void findByTokenHash() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/oauth/claude-code-client-metadata"));
        var saved = tokens.save(token(connection, AgentConnectionTokenKind.ACCESS, "axa_find", NOW.plusSeconds(3600)));

        // when
        var actual = tokens.findByTokenHash(TokenHasher.sha256Hex("axa_find"));

        // then
        assertThat(actual).isPresent().get().extracting(AgentConnectionTokenEntity::getId).isEqualTo(saved.getId());
        assertThat(actual.get().getKind()).isEqualTo(AgentConnectionTokenKind.ACCESS);
    }

    @Test
    void consume_succeedsOnce() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/a"));
        var code = tokens.save(token(connection, AgentConnectionTokenKind.CODE, "axc_once", NOW.plusSeconds(60)));

        // when
        var first = tokens.consume(code.getId(), NOW);
        var second = tokens.consume(code.getId(), NOW);

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(tokens.findById(code.getId()).orElseThrow().getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteAccessTokens_leavesOtherKinds() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/b"));
        tokens.save(token(connection, AgentConnectionTokenKind.ACCESS, "axa_gone", NOW.plusSeconds(3600)));
        tokens.save(token(connection, AgentConnectionTokenKind.REFRESH, "axr_kept", NOW.plusSeconds(3600)));

        // when
        tokens.deleteAccessTokens(connection.getId());

        // then
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex("axa_gone"))).isEmpty();
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex("axr_kept"))).isPresent();
    }

    @Test
    void deleteExpired() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/c"));
        tokens.save(token(connection, AgentConnectionTokenKind.CODE, "axc_old", NOW.minusSeconds(1)));
        tokens.save(token(connection, AgentConnectionTokenKind.REFRESH, "axr_live", NOW.plusSeconds(60)));

        // when
        var actual = tokens.deleteExpired("default", NOW);

        // then
        assertThat(actual).isEqualTo(1);
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex("axr_live"))).isPresent();
    }

    @Test
    void findActiveForClient_skipsPendingAndRevoked() {

        // given
        var clientId = "https://claude.ai/d";
        connections.save(pending("u-1", clientId));
        connections.save(pending("u-1", clientId).toBuilder().activatedAt(NOW).revokedAt(NOW).build());
        var active = connections.save(pending("u-1", clientId).toBuilder().activatedAt(NOW).build());

        // when
        var actual = connections.findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
                "default", "u-1", clientId);

        // then
        assertThat(actual).extracting(AgentConnectionEntity::getId).containsExactly(active.getId());
    }

    @Test
    void save_refusesASecondActiveConnectionForTheSameClient() {

        // given
        var clientId = "https://claude.ai/e";
        connections.save(pending("u-1", clientId).toBuilder().activatedAt(NOW).build());

        // when / then
        assertThatThrownBy(() -> connections.save(pending("u-1", clientId).toBuilder().activatedAt(NOW).build()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deleteRetired_removesStalePendingAndLongRetiredRows_andCascadesTheirTokens() {

        // given
        var stalePending = connections.save(pending("u-1", "https://claude.ai/f"));
        jdbc.update("UPDATE agent_connection SET created_at = :at WHERE id = :id",
                java.util.Map.of("at", java.sql.Timestamp.from(NOW.minusSeconds(172_800)), "id", stalePending.getId()));
        tokens.save(token(stalePending, AgentConnectionTokenKind.CODE, "axc_stale", NOW.plusSeconds(60)));
        var longRevoked = connections.save(pending("u-1", "https://claude.ai/g").toBuilder().activatedAt(NOW)
                .revokedAt(NOW.minusSeconds(40L * 86_400)).build());
        var recentlyRevoked = connections.save(pending("u-1", "https://claude.ai/h").toBuilder().activatedAt(NOW)
                .revokedAt(NOW.minusSeconds(86_400)).build());
        var live = connections.save(pending("u-1", "https://claude.ai/i").toBuilder().activatedAt(NOW).build());

        // when
        var actual = connections.deleteRetired("default", NOW.minusSeconds(86_400), NOW.minusSeconds(30L * 86_400));

        // then
        assertThat(actual).isEqualTo(2);
        assertThat(connections.findById(stalePending.getId())).isEmpty();
        assertThat(connections.findById(longRevoked.getId())).isEmpty();
        assertThat(connections.findById(recentlyRevoked.getId())).isPresent();
        assertThat(connections.findById(live.getId())).isPresent();
        assertThat(tokens.findByTokenHash(TokenHasher.sha256Hex("axc_stale"))).isEmpty();
    }

    @Test
    void touchLastUsed() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/j").toBuilder().activatedAt(NOW).build());

        // when
        connections.touchLastUsed("default", connection.getId(), NOW, NOW.minusSeconds(60));

        // then
        assertThat(connections.findById(connection.getId()).orElseThrow().getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void touchLastUsed_writesAtMostOncePerMinute() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/m7").toBuilder().activatedAt(NOW).build());
        connections.touchLastUsed("default", connection.getId(), NOW, NOW.minusSeconds(60));

        // when
        var soon = NOW.plusSeconds(59);
        connections.touchLastUsed("default", connection.getId(), soon, soon.minusSeconds(60));
        var skipped = connections.findById(connection.getId()).orElseThrow().getLastUsedAt();
        var later = NOW.plusSeconds(61);
        connections.touchLastUsed("default", connection.getId(), later, later.minusSeconds(60));

        // then
        assertThat(skipped).isEqualTo(NOW);
        assertThat(connections.findById(connection.getId()).orElseThrow().getLastUsedAt()).isEqualTo(later);
    }

    @Test
    void aTokenCannotBelongToAnotherOrganizationThanItsConnection() {

        // given
        var connection = connections.save(pending("u-1", "https://claude.ai/m9"));
        var foreign = token(connection, AgentConnectionTokenKind.CODE, "axc_foreign", NOW.plusSeconds(60)).toBuilder()
                .organizationId("acme").build();

        // when / then
        assertThatThrownBy(() -> tokens.save(foreign)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AgentConnectionEntity pending(String userId, String clientId) {

        return AgentConnectionEntity.builder().organizationId("default").userId(userId).clientId(clientId)
                .clientName("Claude Code").clientHost("claude.ai").scopes("read").resource("https://axonia.test/mcp")
                .redirectUri("http://localhost:3118/callback")
                .codeChallenge("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM").endsAt(NOW.plusSeconds(86_400)).build();
    }

    private static AgentConnectionTokenEntity token(AgentConnectionEntity connection, AgentConnectionTokenKind kind,
            String plaintext, Instant expiresAt) {

        return AgentConnectionTokenEntity.builder().organizationId("default").connectionId(connection.getId())
                .kind(kind).tokenHash(TokenHasher.sha256Hex(plaintext)).expiresAt(expiresAt).build();
    }
}
