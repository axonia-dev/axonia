package dev.axonia.events.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.SlotSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SlotSourceAccessAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private SlotSourceAccessAdapter sut;

    @Test
    void slotSources_returnsSessionAndSectionTypePerSlot() {

        sut = new SlotSourceAccessAdapter(jdbc);
        var sectionTypeId = seedSectionType("Workshop", "wrench", "#123456");
        var sessionId = seedSession();
        var sectionId = seedSection(sessionId, sectionTypeId);
        var slot = seedSlot(sectionId, 1, "Scaling Kafka");

        assertThat(sut.slotSources(List.of(slot)))
                .containsOnly(entry(slot, new SlotSource(sessionId, sectionTypeId, "Workshop", "wrench", "#123456")));
    }

    @Test
    void slotSources_emptyInput_returnsEmptyMap() {

        sut = new SlotSourceAccessAdapter(jdbc);

        assertThat(sut.slotSources(List.of())).isEmpty();
    }

    @Test
    void slotContribution_returnsAttributionForPresentedSlot() {

        sut = new SlotSourceAccessAdapter(jdbc);
        var sectionTypeId = seedSectionType("Workshop", "wrench", "#123456");
        var seeded = seedSessionWithCommunity();
        var sectionId = seedSection(seeded.sessionId(), sectionTypeId);
        var slot = seedSlot(sectionId, 1, "Scaling Kafka");
        seedPresenter(slot, "alice");
        seedPresenter(slot, "bob");

        var actual = sut.slotContribution("default", slot);

        assertThat(actual).isPresent();
        assertThat(actual.get().communityId()).isEqualTo(seeded.communityId());
        assertThat(actual.get().title()).isEqualTo("Scaling Kafka");
        assertThat(actual.get().contributorUserIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void slotContribution_slotWithoutTitle_fallsBackToSessionTitle() {

        sut = new SlotSourceAccessAdapter(jdbc);
        var sectionTypeId = seedSectionType("Workshop", "wrench", "#123456");
        var seeded = seedSessionWithCommunity("Session Title");
        var sectionId = seedSection(seeded.sessionId(), sectionTypeId);
        var slot = seedSlot(sectionId, 1, null);

        var actual = sut.slotContribution("default", slot);

        assertThat(actual).isPresent();
        assertThat(actual.get().title()).isEqualTo("Session Title");
        assertThat(actual.get().contributorUserIds()).isEmpty();
    }

    @Test
    void slotContribution_unknownSlot_returnsEmpty() {

        sut = new SlotSourceAccessAdapter(jdbc);

        assertThat(sut.slotContribution("default", -1L)).isEmpty();
    }

    @Test
    void slotContribution_wrongOrganization_returnsEmpty() {

        sut = new SlotSourceAccessAdapter(jdbc);
        var sectionTypeId = seedSectionType("Workshop", "wrench", "#123456");
        var seeded = seedSessionWithCommunity();
        var sectionId = seedSection(seeded.sessionId(), sectionTypeId);
        var slot = seedSlot(sectionId, 1, "Scaling Kafka");

        assertThat(sut.slotContribution("other-org", slot)).isEmpty();
    }

    @Test
    void slotsPresentedBy_returnsSlotsTheUserCurrentlyPresents_orgScoped() {

        sut = new SlotSourceAccessAdapter(jdbc);
        var sectionTypeId = seedSectionType("Workshop", "wrench", "#123456");
        var seeded = seedSessionWithCommunity();
        var sectionId = seedSection(seeded.sessionId(), sectionTypeId);
        var slot = seedSlot(sectionId, 1, "Scaling Kafka");
        var otherSlot = seedSlot(sectionId, 2, "Other Talk");
        seedPresenter(slot, "alice");
        seedPresenter(otherSlot, "bob");

        assertThat(sut.slotsPresentedBy("default", "alice")).containsExactly(slot);
        assertThat(sut.slotsPresentedBy("other-org", "alice")).isEmpty();
    }

    private void seedPresenter(Long slotId, String userId) {

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :userId)",
                new MapSqlParameterSource("slotId", slotId).addValue("userId", userId));
    }

    private record SeededSession(Long communityId, Long sessionId) {
    }

    private SeededSession seedSessionWithCommunity() {

        return seedSessionWithCommunity(null);
    }

    private SeededSession seedSessionWithCommunity(String title) {

        var communityId = seedCommunity();
        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'SS Series', 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId), Long.class);
        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, title, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), now()::date, '', '', :title, true, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId).addValue("title",
                        title),
                Long.class);
        return new SeededSession(communityId, sessionId);
    }

    private Long seedCommunity() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'SlotSourceKind', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'slot-source-' || nextval('communities_id_seq'), 'SS', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='SlotSourceKind' RETURNING id",
                new MapSqlParameterSource(), Long.class);
    }

    private Long seedSectionType(String label, String icon, String accentColor) {

        return jdbc.queryForObject("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', :label, true, now(), now(), 0, :icon, :accentColor) RETURNING id",
                new MapSqlParameterSource("label", label).addValue("icon", icon).addValue("accentColor", accentColor),
                Long.class);
    }

    private Long seedSession() {

        return seedSessionWithCommunity().sessionId();
    }

    private Long seedSection(Long sessionId, Long sectionTypeId) {

        return jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', 0, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId), Long.class);
    }

    private Long seedSlot(Long sectionId, int position, String title) {

        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, title, duration_minutes) "
                        + "VALUES (:sectionId, :position, :title, 20) RETURNING id",
                new MapSqlParameterSource("sectionId", sectionId).addValue("position", position).addValue("title",
                        title),
                Long.class);
    }
}
