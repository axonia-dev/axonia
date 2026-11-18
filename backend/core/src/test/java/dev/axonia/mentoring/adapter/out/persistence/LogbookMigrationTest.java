package dev.axonia.mentoring.adapter.out.persistence;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V137 runs against notes written before a note carried its meeting day: each takes the day it was written in its
 * author's zone, and the newest next meeting a note carried moves to its ongoing pairing.
 */
class LogbookMigrationTest {

    private static final String LATE_EVENING_UTC = "2026-11-15T12:30:00Z";

    private static DedicatedPostgresContainer pg;
    private static JdbcTemplate jdbc;

    private static long ongoing;
    private static long ended;
    private static long aucklandNote;
    private static long newYorkNote;
    private static long unknownZoneNote;
    private static long noProfileNote;

    @BeforeAll
    static void migrateAroundOldNotes() {

        pg = new DedicatedPostgresContainer();
        pg.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        MigrationChain.SELF_HOSTED.upTo(pg, "136").migrate();
        user("mia", "Pacific/Auckland", null);
        user("max", null, "America/New_York");
        user("lea", "Mars/Olympus_Mons", null);
        ongoing = pairing("max", null);
        ended = pairing("lea", "2026-11-14T10:00:00Z");
        aucklandNote = note(ongoing, "mia", LATE_EVENING_UTC, "2026-11-25", null);
        newYorkNote = note(ongoing, "max", "2026-11-16T03:00:00Z", "2026-11-28", "2026-11-16T04:00:00Z");
        note(ongoing, "max", "2026-11-14T03:00:00Z", "2026-11-30", null);
        unknownZoneNote = note(ended, "lea", LATE_EVENING_UTC, "2026-11-20", null);
        noProfileNote = note(ended, "ghost", "2026-11-15T23:30:00Z", null, null);
        MigrationChain.SELF_HOSTED.upTo(pg, "142").migrate();
    }

    @AfterAll
    static void stop() {

        pg.stop();
    }

    @Test
    void aNoteTakesItsWrittenDayInItsAuthorsZone() {

        // when / then
        assertThat(meetingOn(aucklandNote)).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(meetingOn(newYorkNote)).isEqualTo(LocalDate.parse("2026-11-15"));
        assertThat(meetingOn(unknownZoneNote)).isEqualTo(LocalDate.parse("2026-11-15"));
        assertThat(meetingOn(noProfileNote)).isEqualTo(LocalDate.parse("2026-11-15"));
    }

    @Test
    void theNewestNextMeetingMovesToTheOngoingPairing() {

        // when
        var actual = nextMeetingOf(ongoing);

        // then
        assertThat(actual).containsEntry("next_meeting_on", Date.valueOf("2026-11-28"))
                .containsEntry("next_meeting_set_by", "max");
        assertThat(((Timestamp) actual.get("next_meeting_set_at")).toInstant())
                .isEqualTo(Instant.parse("2026-11-16T04:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = "
                + "'mentoring_note' AND column_name = 'next_meeting_on'", Long.class)).isZero();
    }

    @Test
    void anEndedPairingTakesNoNextMeeting() {

        // when
        var actual = nextMeetingOf(ended);

        // then
        assertThat(actual).containsEntry("next_meeting_on", null).containsEntry("next_meeting_set_by", null)
                .containsEntry("next_meeting_set_at", null);
    }

    private static void user(String userId, String declared, String observed) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           declared_time_zone, observed_time_zone, created_at, updated_at, version)
                VALUES ('default', ?, ?, ? || '@example.test', true, 'scim-' || ?, 'SYNCED', ?, ?, now(), now(), 0)
                """, userId, userId, userId, userId, declared, observed);
    }

    private static long pairing(String mentee, String endedAt) {

        return jdbc.queryForObject("INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, "
                + "mentee_user_id, started_at, ended_at, end_reason) VALUES ('default', 1, 'mia', ?, "
                + "'2026-11-01T09:00:00Z', ?::timestamptz, CASE WHEN ?::text IS NULL THEN NULL ELSE 'MENTOR' END) "
                + "RETURNING id", Long.class, mentee, endedAt, endedAt);
    }

    private static long note(long pairingId, String author, String writtenAt, String nextMeetingOn, String editedAt) {

        return jdbc.queryForObject("INSERT INTO mentoring_note (organization_id, pairing_id, author_user_id, body, "
                + "next_meeting_on, written_at, edited_at) VALUES ('default', ?, ?, 'Met', ?::date, ?::timestamptz, "
                + "?::timestamptz) RETURNING id", Long.class, pairingId, author, nextMeetingOn, writtenAt, editedAt);
    }

    private static LocalDate meetingOn(long noteId) {

        return jdbc.queryForObject("SELECT meeting_on FROM mentoring_note WHERE id = ?", LocalDate.class, noteId);
    }

    private static Map<String, Object> nextMeetingOf(long pairingId) {

        return jdbc.queryForMap("SELECT next_meeting_on, next_meeting_set_by, next_meeting_set_at FROM "
                + "mentoring_pairing WHERE id = ?", pairingId);
    }
}
