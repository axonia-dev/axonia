package dev.axonia.communication.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs as the Testcontainers superuser, so - exactly like {@link JdbcOutboxTest} - {@link #authenticate()} sets
 * {@code app.current_org} via {@code @BeforeTransaction} for {@link JdbcProductionWatermark#since}'s
 * {@code current_setting} read to see anything at all; see that class's own javadoc for why the timing matters.
 */
class JdbcProductionWatermarkTest extends CommunicationJdbcTestSupport {

    private static final String PRODUCER_KEY = "session-wrap-up";
    private static final Instant FLOOR = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcProductionWatermark watermark;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeTransaction
    void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void sinceFallsBackToTheFloorWhenNoRowExistsYet() {

        assertThat(watermark.since(PRODUCER_KEY, FLOOR)).isEqualTo(FLOOR);
    }

    @Test
    void sinceReturnsTheStoredWatermarkOnceOneHasBeenAdvancedTo() {

        var advancedTo = FLOOR.plus(Duration.ofDays(10));

        watermark.advanceTo(PRODUCER_KEY, advancedTo);

        assertThat(watermark.since(PRODUCER_KEY, FLOOR)).isEqualTo(advancedTo);
    }

    @Test
    void advanceToOverwritesRatherThanAccumulatesOnASecondCall() {

        watermark.advanceTo(PRODUCER_KEY, FLOOR.plus(Duration.ofDays(1)));
        watermark.advanceTo(PRODUCER_KEY, FLOOR.plus(Duration.ofDays(2)));

        assertThat(watermark.since(PRODUCER_KEY, FLOOR)).isEqualTo(FLOOR.plus(Duration.ofDays(2)));
        assertThat(countRows()).isEqualTo(1);
    }

    /**
     * The {@code GREATEST} guard: two concurrent instances racing this upsert must never let the one that happens to
     * commit second move the watermark backwards, whatever order they actually land in.
     */
    @Test
    void advanceToNeverMovesTheWatermarkBackwards() {

        watermark.advanceTo(PRODUCER_KEY, FLOOR.plus(Duration.ofDays(2)));
        watermark.advanceTo(PRODUCER_KEY, FLOOR.plus(Duration.ofDays(1)));

        assertThat(watermark.since(PRODUCER_KEY, FLOOR)).isEqualTo(FLOOR.plus(Duration.ofDays(2)));
    }

    /** Two producer arms of the same organization never share a row, even at the identical instant. */
    @Test
    void distinctProducerKeysAdvanceIndependently() {

        var now = FLOOR.plus(Duration.ofDays(5));
        watermark.advanceTo("session-awaiting-close-1d", now);
        watermark.advanceTo("session-awaiting-close-7d", now.plus(Duration.ofDays(1)));

        assertThat(watermark.since("session-awaiting-close-1d", FLOOR)).isEqualTo(now);
        assertThat(watermark.since("session-awaiting-close-7d", FLOOR)).isEqualTo(now.plus(Duration.ofDays(1)));
    }

    /**
     * {@code since}'s defence-in-depth predicate reads {@code current_setting('app.current_org', true)}, not
     * {@link JdbcOutbox}'s injected {@code OrganizationContext} - see this class's own javadoc for why an unscoped
     * connection must see nothing rather than another organization's row.
     */
    @Test
    void sinceIsScopedToTheCurrentOrganization() {

        jdbc.update(
                "INSERT INTO communication_production_watermark (organization_id, producer_key, produced_through, "
                        + "updated_at, version) VALUES ('some-other-org', :key, :now, :now, 0)",
                Map.of("key", PRODUCER_KEY, "now", java.sql.Timestamp.from(FLOOR.plus(Duration.ofDays(30)))));

        assertThat(watermark.since(PRODUCER_KEY, FLOOR)).isEqualTo(FLOOR);
    }

    private long countRows() {

        var count = jdbc.queryForObject(
                "SELECT count(*) FROM communication_production_watermark WHERE organization_id = 'acme' "
                        + "AND producer_key = :key",
                Map.of("key", PRODUCER_KEY), Long.class);
        return count == null ? 0 : count;
    }
}
