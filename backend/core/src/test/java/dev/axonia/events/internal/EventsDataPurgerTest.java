package dev.axonia.events.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class EventsDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private EventsDataPurger purger;

    @Test
    void purgeEmptiesEveryEventsTableForTheOrganization() {

        // given — a full FK chain of events rows for org "default"
        seedEventsGraph();

        // when
        purger.purge(ORG);

        // then — every events table is empty for the org, children included
        assertThat(count("community_session")).isZero();
        assertThat(count("event_series")).isZero();
        assertThat(count("session_attendance")).isZero();
        assertThat(count("topic")).isZero();
        assertThat(count("topic_vote")).isZero();
        assertThat(count("event_section_type")).isZero();
        assertThat(countAll("event_section")).isZero();
        assertThat(countAll("event_slot")).isZero();
        assertThat(countAll("event_slot_presenter")).isZero();
        assertThat(countAll("event_slot_tag")).isZero();
        assertThat(countAll("event_series_section_type")).isZero();
        assertThat(count("calendar_subscription")).isZero();
        assertThat(count("action_item")).isZero();
        assertThat(countAll("action_item_tag")).isZero();
        assertThat(count("action_item_checkpoint")).isZero();
    }

    /**
     * Seeds one row per events table, wired through the real FK chain: community (owned by the community module, seeded
     * directly here) &lt;- series/topic &lt;- session &lt;- section &lt;- slot &lt;- slot presenter, plus the
     * series/session structure join table, attendance, votes, and the action item chain (action item &lt;- tag,
     * checkpoint) rooted at the same session.
     */
    private void seedEventsGraph() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES (:org, 'Purger Test Guild', now(), now(), 0)", Map.of("org", ORG));
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) SELECT :org, 'purger-test-community', 'Purger Test Community', k.id, "
                        + "'', 'ACTIVE', now(), now(), 0 FROM community_kind k WHERE k.organization_id = :org "
                        + "AND k.label = 'Purger Test Guild' RETURNING id",
                Map.of("org", ORG), Long.class);

        var sectionTypeId = jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, "
                        + "version) VALUES (:org, 'Purger Test Section Type', true, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, status, "
                        + "created_at, updated_at, version, starts_on, time_zone) VALUES (:org, :communityId, "
                        + "'Purger Test Series', 1, 'WEEK', 'ACTIVE', now(), now(), 0, CURRENT_DATE, 'UTC') RETURNING id",
                Map.of("org", ORG, "communityId", communityId), Long.class);

        jdbc.update(
                "INSERT INTO event_series_section_type (series_id, \"position\", section_type_id, title, "
                        + "duration_minutes) VALUES (:seriesId, 0, :sectionTypeId, 'Default Structure', 15)",
                Map.of("seriesId", seriesId, "sectionTypeId", sectionTypeId));

        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, "
                        + "notes, recording_url, has_time, created_at, updated_at, version, occurrence_date) VALUES (:org, "
                        + ":seriesId, :communityId, 'SCHEDULED', now(), '', '', true, now(), now(), 0, CURRENT_DATE) "
                        + "RETURNING id",
                Map.of("org", ORG, "seriesId", seriesId, "communityId", communityId), Long.class);

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id, title) VALUES "
                        + "(:sessionId, 0, :sectionTypeId, 'Purger Test Section') RETURNING id",
                Map.of("sessionId", sessionId, "sectionTypeId", sectionTypeId), Long.class);

        var slotId = jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes, title) VALUES "
                        + "(:sectionId, 0, 10, 'Purger Test Slot') RETURNING id",
                Map.of("sectionId", sectionId), Long.class);

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, 'presenter-user')",
                Map.of("slotId", slotId));

        jdbc.update("INSERT INTO event_slot_tag (slot_id, tag) VALUES (:slotId, 'kafka')", Map.of("slotId", slotId));

        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, "
                        + "version) VALUES (:org, :sessionId, 'attendee-user', now(), now(), 0)",
                Map.of("org", ORG, "sessionId", sessionId));

        var topicId = jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, 'REQUEST', "
                        + "'Purger Test Topic', '', 'proposer-user', 'PROPOSED', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId), Long.class);

        jdbc.update(
                "INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version) "
                        + "VALUES (:org, :topicId, 'voter-user', now(), now(), 0)",
                Map.of("org", ORG, "topicId", topicId));

        jdbc.update("INSERT INTO calendar_subscription (organization_id, community_id, user_id, token_hash, "
                + "created_at, version) VALUES (:org, :communityId, 'subscriber-user', "
                + "'purger-test-subscription-hash', now(), 0)", Map.of("org", ORG, "communityId", communityId));

        var actionItemId = jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, origin_session_id, outcome, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, 'Purger Test Action Item', '', 'raiser-user', 'assignee-user', "
                        + ":sessionId, 'OPEN', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "sessionId", sessionId), Long.class);

        jdbc.update("INSERT INTO action_item_tag (action_item_id, tag) VALUES (:actionItemId, 'kafka')",
                Map.of("actionItemId", actionItemId));

        jdbc.update(
                "INSERT INTO action_item_checkpoint (organization_id, action_item_id, session_id, outcome, "
                        + "decided_by_user_id, decided_at) VALUES (:org, :actionItemId, :sessionId, 'CARRIED', "
                        + "'decider-user', now())",
                Map.of("org", ORG, "actionItemId", actionItemId, "sessionId", sessionId));
    }

    private long count(String table) {

        return switch (table) {
            case "community_session" ->
                jdbc.queryForObject("SELECT count(*) FROM community_session WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "event_series" -> jdbc.queryForObject("SELECT count(*) FROM event_series WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "session_attendance" ->
                jdbc.queryForObject("SELECT count(*) FROM session_attendance WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "topic" -> jdbc.queryForObject("SELECT count(*) FROM topic WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "topic_vote" -> jdbc.queryForObject("SELECT count(*) FROM topic_vote WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "event_section_type" ->
                jdbc.queryForObject("SELECT count(*) FROM event_section_type WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "calendar_subscription" ->
                jdbc.queryForObject("SELECT count(*) FROM calendar_subscription WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "action_item" -> jdbc.queryForObject("SELECT count(*) FROM action_item WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "action_item_checkpoint" ->
                jdbc.queryForObject("SELECT count(*) FROM action_item_checkpoint WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }

    private long countAll(String table) {

        return switch (table) {
            case "event_section" -> jdbc.queryForObject("SELECT count(*) FROM event_section", Map.of(), Long.class);
            case "event_slot" -> jdbc.queryForObject("SELECT count(*) FROM event_slot", Map.of(), Long.class);
            case "event_slot_presenter" ->
                jdbc.queryForObject("SELECT count(*) FROM event_slot_presenter", Map.of(), Long.class);
            // no explicit DELETE in the purger: event_slot_tag cascades from event_slot, like topic_tag from topic
            case "event_slot_tag" -> jdbc.queryForObject("SELECT count(*) FROM event_slot_tag", Map.of(), Long.class);
            case "event_series_section_type" ->
                jdbc.queryForObject("SELECT count(*) FROM event_series_section_type", Map.of(), Long.class);
            // no explicit DELETE in the purger: action_item_tag cascades from action_item, like topic_tag from topic
            case "action_item_tag" -> jdbc.queryForObject("SELECT count(*) FROM action_item_tag", Map.of(), Long.class);
            default -> throw new IllegalArgumentException("Unexpected table: " + table);
        };
    }
}
