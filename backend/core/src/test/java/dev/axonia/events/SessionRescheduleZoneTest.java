package dev.axonia.events;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which zone a rescheduled occurrence is read in, proved against a community that does not meet in UTC.
 *
 * <p>
 * The reschedule dialog used to hand the server an {@code Instant} it had composed itself, out of the browser's own
 * zone, and say so nowhere. An organizer working from another country therefore moved a community's session by an
 * unnoticed offset, and no test could see it because every fixture in the suite meets in UTC, where the browser's
 * reading and the community's reading are the same number.
 *
 * <p>
 * So the series here is {@code Europe/Paris} and the request states {@code America/New_York}, six hours apart in
 * August. Only the composition that ignores the request's zone answers 15:00Z; the reading the old client would have
 * sent answers 21:00Z. The one-off case is the other half of the same rule: it belongs to no series, so nothing but the
 * caller's zone can govern it, and its answer is the New York one.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class SessionRescheduleZoneTest {

    private static final String ORG = "default";
    private static final String ORGANIZER = "reschedule-zone-organizer";

    /** The zone the community meets in. UTC+2 on the date every case below moves a session to. */
    private static final String PARIS = "Europe/Paris";

    /** The zone the request states. UTC-4 on that date, so the two readings are six hours apart. */
    private static final String NEW_YORK = "America/New_York";

    private static final LocalDate MOVED_TO = LocalDate.of(2026, 8, 13);

    /** A community of its own per test: the fixtures commit, and are only truncated once the class is done. */
    private static final AtomicInteger COMMUNITIES = new AtomicInteger();

    @Autowired
    private SeriesService series;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    private Long seriesOccurrenceId;
    private Long oneOffId;

    @BeforeEach
    void seedACommunityThatMeetsInParis() {

        var slug = "reschedule-zone-" + COMMUNITIES.incrementAndGet();
        organizerOf(slug);

        var dojo = series.start(slug, new CreateSeriesRequest("Dojo", new Cadence(1, CadenceUnit.WEEK),
                LocalDate.of(2026, 8, 6), LocalTime.of(18, 0), PARIS, null, 60, List.of()));
        seriesOccurrenceId = sessions.ensure(dojo.id(), MOVED_TO).id();
        oneOffId = sessions
                .createOneOff(slug, new CreateOneOffRequest("Lightning talks",
                        new ScheduledAt(LocalDate.of(2026, 8, 6), LocalTime.of(18, 0), PARIS), null, 60, List.of()))
                .id();
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a series occurrence composes in its series' zone, whatever zone the request states")
    void seriesZoneWins() {

        var session = rescheduleTo(seriesOccurrenceId, new ScheduledAt(MOVED_TO, LocalTime.of(17, 0), NEW_YORK));

        // 17:00 in Europe/Paris on that date is 15:00Z. The request's own zone is ignored.
        assertThat(session.scheduledAt()).isEqualTo(Instant.parse("2026-08-13T15:00:00Z"));
    }

    /**
     * The same move read the way the old client would have sent it, so the case above cannot pass by coincidence: if
     * the two zones ever agreed on this date, this assertion would fail and say so.
     */
    @Test
    @DisplayName("the zone the request states really is a different instant")
    void theTwoReadingsGenuinelyDiffer() {

        var paris = rescheduleTo(seriesOccurrenceId, new ScheduledAt(MOVED_TO, LocalTime.of(17, 0), NEW_YORK));

        assertThat(paris.scheduledAt()).isNotEqualTo(Instant.parse("2026-08-13T21:00:00Z"));
        assertThat(paris.seriesTimeZone()).isEqualTo(PARIS);
    }

    @Test
    @DisplayName("a one-off has no series zone, so the request's zone is what composes it")
    void oneOffUsesTheRequestZone() {

        var session = rescheduleTo(oneOffId, new ScheduledAt(MOVED_TO, LocalTime.of(17, 0), NEW_YORK));

        assertThat(session.scheduledAt()).isEqualTo(Instant.parse("2026-08-13T21:00:00Z"));
        assertThat(session.seriesTimeZone()).isNull();
    }

    /**
     * The request states New York rather than the series' own zone, though it is clearing the time rather than setting
     * one. Stating Paris here would make the case agree with the series by coincidence and prove only the arithmetic:
     * midnight in New York is 04:00Z, so only the composition that ignores the request's zone answers 22:00Z the day
     * before.
     */
    @Test
    @DisplayName("clearing the time leaves the occurrence untimed at midnight in its series' zone")
    void clearingTheTimeGoesUntimed() {

        var session = rescheduleTo(seriesOccurrenceId, new ScheduledAt(MOVED_TO, null, NEW_YORK));

        assertThat(session.hasTime()).isFalse();
        assertThat(session.scheduledAt()).isEqualTo(Instant.parse("2026-08-12T22:00:00Z"));
    }

    @Test
    @DisplayName("a one-off may not be untimed")
    void oneOffMayNotBeUntimed() {

        assertThatThrownBy(() -> rescheduleTo(oneOffId, new ScheduledAt(MOVED_TO, null, PARIS)))
                .isInstanceOf(ValidationException.class);
    }

    private Session rescheduleTo(Long sessionId, ScheduledAt scheduledAt) {

        return sessions.update(sessionId, new UpdateSessionRequest(scheduledAt, 60, ORGANIZER, null));
    }

    private void organizerOf(String slug) {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (?, 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING", ORG);
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, "
                + "arrival, created_at, updated_at, version) VALUES (?, ?, 'Zone Organizer', null, true, "
                + "'scim-reschedule-zone', 'SYNCED', now(), now(), 0) ON CONFLICT DO NOTHING", ORG, ORGANIZER);
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', "
                        + "(SELECT min(id) FROM community_kind WHERE organization_id = ?), now(), now(), 0) "
                        + "RETURNING id",
                Long.class, ORG, slug, slug, ORG);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'ORGANIZER', now(), now(), 0)", ORG, communityId, ORGANIZER);

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(ORGANIZER).claim("org", ORG).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
