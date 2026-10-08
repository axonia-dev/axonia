package dev.axonia.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SlotSessionReaderTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SlotSessionReader sut;

    @Test
    void sessionIdOf_returnsOwningSession() {

        sut = new SlotSessionReader(jdbc);
        var sessionId = seedSession();
        var sectionId = seedSection(sessionId);
        var slotId = seedSlot(sectionId);

        assertThat(sut.sessionIdOf(slotId)).contains(sessionId);
    }

    @Test
    void sessionIdOf_unknownSlot_returnsEmpty() {

        sut = new SlotSessionReader(jdbc);

        assertThat(sut.sessionIdOf(999_999L)).isEmpty();
    }

    private Long seedSession() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'Community of Practice', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'slot-session-' || nextval('communities_id_seq'), 'SS', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' LIMIT 1 RETURNING id",
                new MapSqlParameterSource(), Long.class);
        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'SS Series', 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), now()::date, '', '', true, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId), Long.class);
    }

    private Long seedSection(Long sessionId) {

        jdbc.update("INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, "
                + "updated_at, version) VALUES ('default', 'Deep Dive', true, now(), now(), 0) "
                + "ON CONFLICT DO NOTHING", new MapSqlParameterSource());
        var sectionTypeId = jdbc.queryForObject(
                "SELECT id FROM event_section_type WHERE organization_id = 'default' LIMIT 1",
                new MapSqlParameterSource(), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', 0, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId), Long.class);
    }

    private Long seedSlot(Long sectionId) {

        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, title, duration_minutes) "
                        + "VALUES (:sectionId, 1, 'Deep dive', 20) RETURNING id",
                new MapSqlParameterSource("sectionId", sectionId), Long.class);
    }
}
