package dev.axonia.events.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The seed helpers are copied from {@code SlotSourceAccessAdapterTest} rather than shared: hoisting them into a base
 * class would add an {@code @Import} set, and so a second Spring context and a second Hikari pool against the shared
 * Testcontainers Postgres.
 */
class SlotAttendanceReaderTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void attendanceBySlot_countsThePeopleWhoAttendedTheSlotsSession() {

        var sut = new SlotAttendanceReader(jdbc);
        var sessionId = seedSession();
        var slot = seedSlot(seedSection(sessionId, seedSectionType()), 1);
        seedAttendance(sessionId, "alice");
        seedAttendance(sessionId, "bob");

        assertThat(sut.attendanceBySlot(List.of(slot))).containsOnly(entry(slot, 2));
    }

    @Test
    void attendanceBySlot_slotWithNoAttendance_countsZero() {

        var sut = new SlotAttendanceReader(jdbc);
        var slot = seedSlot(seedSection(seedSession(), seedSectionType()), 1);

        assertThat(sut.attendanceBySlot(List.of(slot))).containsOnly(entry(slot, 0));
    }

    @Test
    void attendanceBySlot_unknownSlot_isAbsentRatherThanZero() {

        var sut = new SlotAttendanceReader(jdbc);

        assertThat(sut.attendanceBySlot(List.of(999_999L))).isEmpty();
    }

    @Test
    void attendanceBySlot_emptyInput_returnsEmptyMap() {

        assertThat(new SlotAttendanceReader(jdbc).attendanceBySlot(List.of())).isEmpty();
    }

    @Test
    void attendanceBySlot_countsPerSlotAcrossSessions() {

        var sut = new SlotAttendanceReader(jdbc);
        var sectionTypeId = seedSectionType();
        var busySession = seedSession();
        var quietSession = seedSession();
        var busySlot = seedSlot(seedSection(busySession, sectionTypeId), 1);
        var quietSlot = seedSlot(seedSection(quietSession, sectionTypeId), 1);
        seedAttendance(busySession, "alice");

        assertThat(sut.attendanceBySlot(List.of(busySlot, quietSlot))).containsOnly(entry(busySlot, 1),
                entry(quietSlot, 0));
    }

    private void seedAttendance(Long sessionId, String userId) {

        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, version) "
                        + "VALUES ('default', :sessionId, :userId, now(), now(), 0)",
                new MapSqlParameterSource("sessionId", sessionId).addValue("userId", userId));
    }

    private Long seedSession() {

        var communityId = seedCommunity();
        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'SA Series', 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, title, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), now()::date, '', '', 'SA Session', true, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId), Long.class);
    }

    private Long seedCommunity() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'SlotAttendanceKind', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'slot-attendance-' || nextval('communities_id_seq'), 'SA', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='SlotAttendanceKind' RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedSectionType() {

        return jdbc.queryForObject("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', 'Workshop', true, now(), now(), 0, 'wrench', '#123456') RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedSection(Long sessionId, Long sectionTypeId) {

        return jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', 0, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId), Long.class);
    }

    private Long seedSlot(Long sectionId, int position) {

        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, title, duration_minutes) "
                        + "VALUES (:sectionId, :position, 'Scaling Kafka', 20) RETURNING id",
                new MapSqlParameterSource("sectionId", sectionId).addValue("position", position), Long.class);
    }
}
