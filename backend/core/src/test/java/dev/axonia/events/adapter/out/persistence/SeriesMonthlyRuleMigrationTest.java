package dev.axonia.events.adapter.out.persistence;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V129 runs against series and sessions written while occurrences were stepped from the previous one, which left a
 * monthly series started on the 31st on the 28th for good after February. Its sessions move back onto the anchors the
 * cadence now computes, so none of them sits beside a projected duplicate.
 */
class SeriesMonthlyRuleMigrationTest {

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long monthlyOn31st;
    private static long monthlyOn13th;
    private static long weekly;
    private static long leapYearly;
    private static long driftedMarch;
    private static long clampedFebruary;
    private static long untouchedMonthly;
    private static long driftedLeapYear;

    @BeforeAll
    static void migrateAroundDriftedSessions() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "128").migrate();
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0)");
        var communityId = jdbc.queryForObject("INSERT INTO communities (organization_id, slug, name, kind_id, "
                + "description, status, created_at, updated_at, version) SELECT 'default', 'guild', 'Guild', k.id, '', "
                + "'ACTIVE', now(), now(), 0 FROM community_kind k RETURNING id", Long.class);
        monthlyOn31st = legacySeries(communityId, "MONTH", "2027-01-31");
        monthlyOn13th = legacySeries(communityId, "MONTH", "2027-01-13");
        weekly = legacySeries(communityId, "WEEK", "2027-01-31");
        leapYearly = legacySeries(communityId, "YEAR", "2028-02-29");
        clampedFebruary = session(communityId, monthlyOn31st, "2027-02-28");
        driftedMarch = session(communityId, monthlyOn31st, "2027-03-28");
        untouchedMonthly = session(communityId, monthlyOn13th, "2027-03-13");
        driftedLeapYear = session(communityId, leapYearly, "2032-02-28");
        session(communityId, weekly, "2027-02-07");
        MigrationChain.SELF_HOSTED.on(pg).migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void existingMonthlySeriesKeepTheirDayNumberAndNoOtherUnitHasARule() {

        assertThat(ruleOf(monthlyOn31st)).isEqualTo("DAY_OF_MONTH");
        assertThat(ruleOf(monthlyOn13th)).isEqualTo("DAY_OF_MONTH");
        assertThat(ruleOf(weekly)).isNull();
        assertThat(ruleOf(leapYearly)).isNull();
    }

    @Test
    void aDriftedSessionReturnsToTheAnchorTheCadenceComputes() {

        assertThat(anchorOf(driftedMarch)).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(anchorOf(driftedLeapYear)).isEqualTo(LocalDate.of(2032, 2, 29));
    }

    @Test
    void aSessionAlreadyOnItsAnchorStaysPut() {

        assertThat(anchorOf(clampedFebruary)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(anchorOf(untouchedMonthly)).isEqualTo(LocalDate.of(2027, 3, 13));
    }

    private static long legacySeries(long communityId, String unit, String startsOn) {

        return jdbc.queryForObject("INSERT INTO event_series (organization_id, community_id, title, cadence_count, "
                + "cadence_unit, starts_on, time_zone, status, created_at, updated_at, version) VALUES ('default', ?, "
                + "'Talks', 1, ?, ?::date, 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id", Long.class, communityId,
                unit, startsOn);
    }

    private static long session(long communityId, long seriesId, String occurrenceDate) {

        return jdbc.queryForObject("INSERT INTO community_session (organization_id, community_id, series_id, status, "
                + "scheduled_at, notes, recording_url, occurrence_date, has_time, created_at, updated_at, version) "
                + "VALUES ('default', ?, ?, 'SCHEDULED', ?::date, '', '', ?::date, false, now(), now(), 0) "
                + "RETURNING id", Long.class, communityId, seriesId, occurrenceDate, occurrenceDate);
    }

    private static String ruleOf(long seriesId) {

        return jdbc.queryForObject("SELECT cadence_monthly_by FROM event_series WHERE id = ?", String.class, seriesId);
    }

    private static LocalDate anchorOf(long sessionId) {

        return jdbc.queryForObject("SELECT occurrence_date FROM community_session WHERE id = ?", LocalDate.class,
                sessionId);
    }
}
