package dev.axonia.events.adapter.out.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ExportFragment;
import dev.axonia.shared.api.personaldata.ExportScope;

import static org.assertj.core.api.Assertions.assertThat;

class EventsPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    private static final String COMMUNITY_NAME = "Exporter Test Community";
    private static final String SERIES_TITLE = "Exporter Test Series";
    private static final String SESSION_TITLE = "Exporter Test Session";
    private static final String TOPIC_TITLE = "Exporter Test Topic";
    private static final String SLOT_TITLE = "Exporter Test Slot";
    private static final String ACTION_ITEM_TITLE = "Exporter Test Action Item";
    private static final LocalDate OCCURRENCE = LocalDate.parse("2026-01-01");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private EventsPersonalDataExporter exporter;

    private Long communityId;
    private Long sectionTypeId;

    @BeforeEach
    void seedFixtures() {

        communityId = seedCommunity();
        sectionTypeId = seedSectionType();
    }

    /**
     * One row per table, one named TARGET and one named NEIGHBOUR every time, including the action item where TARGET is
     * only the assignee, never the raiser - the OR across both {@code action_item} columns is the point, and a version
     * that dropped either half would still pass a test that only ever named a single column. {@code topic} carries only
     * {@code proposer_user_id}, the assignee living on {@code action_item}, so its half of the fixture needs no
     * co-naming at all.
     */
    @Test
    void memberScopedExportCarriesEveryRowWhereTheMemberIsNamedAndNoOneElses() {

        var sessionId = seedSession(TARGET);
        var neighbourSessionId = seedSession(NEIGHBOUR);
        var slotId = seedSlot(sessionId);
        var neighbourSlot = seedSlot(neighbourSessionId);
        var proposedByTarget = seedTopic(TOPIC_TITLE, TARGET);
        seedTopic("Neighbour Topic", NEIGHBOUR);
        seedActionItem(ACTION_ITEM_TITLE, NEIGHBOUR, TARGET);
        seedActionItem("Neighbour Action Item", NEIGHBOUR, NEIGHBOUR);
        seedVote(TARGET, proposedByTarget);
        seedVote(NEIGHBOUR, proposedByTarget);
        seedAttendance(TARGET, sessionId);
        seedAttendance(NEIGHBOUR, sessionId);
        seedPresenter(TARGET, slotId);
        seedPresenter(NEIGHBOUR, neighbourSlot);
        seedSubscription(TARGET, "target-subscription-hash");
        seedSubscription(NEIGHBOUR, "neighbour-subscription-hash");

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("events.json");
        assertThat(onlyRow(fragment, "topic")).containsEntry("proposer_user_id", TARGET).containsEntry("title",
                TOPIC_TITLE);
        assertThat(onlyRow(fragment, "action_item")).containsEntry("assignee_user_id", TARGET).containsEntry("title",
                ACTION_ITEM_TITLE);
        assertThat(onlyRow(fragment, "community_session")).containsEntry("facilitator_user_id", TARGET);
        assertThat(onlyRow(fragment, "topic_vote")).containsEntry("user_id", TARGET);
        assertThat(onlyRow(fragment, "session_attendance")).containsEntry("user_id", TARGET);
        assertThat(onlyRow(fragment, "event_slot_presenter")).containsEntry("user_id", TARGET);
        assertThat(onlyRow(fragment, "calendar_subscription")).containsEntry("user_id", TARGET);
        assertThat(json(fragment)).doesNotContain(NEIGHBOUR);
    }

    /**
     * The narrowing this module exists to prove: a row carries the facts it holds and the names its keys pointed at,
     * and nothing that is only a handle into the database. Asserting the exact key set rather than a few present ones
     * is what makes a key added back to {@code exportedContext} next year a failure here rather than a quiet leak.
     */
    @Test
    void everyTableEmitsItsOwnFactsAndNamesRatherThanKeys() {

        seedTheWholeModuleFor(TARGET);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(onlyRow(fragment, "topic")).containsOnlyKeys("proposer_user_id", "kind", "title", "description",
                "status", "created_at", "updated_at", "community");
        assertThat(onlyRow(fragment, "topic_vote")).containsOnlyKeys("user_id", "created_at", "updated_at", "topic");
        assertThat(onlyRow(fragment, "session_attendance")).containsOnlyKeys("user_id", "created_at", "updated_at",
                "session", "session_date");
        assertThat(onlyRow(fragment, "community_session")).containsOnlyKeys("facilitator_user_id", "status",
                "scheduled_at", "has_time", "time_zone", "duration_minutes", "notes", "recording_url", "location",
                "join_url", "joining_details", "occurrence_date", "title", "closed_at", "created_at", "updated_at",
                "community", "series");
        assertThat(onlyRow(fragment, "event_slot_presenter")).containsOnlyKeys("user_id", "slot", "session",
                "session_date");
        assertThat(onlyRow(fragment, "calendar_subscription")).containsOnlyKeys("user_id", "last_fetched_at",
                "community");
        assertThat(onlyRow(fragment, "action_item")).containsOnlyKeys("raised_by_user_id", "assignee_user_id", "title",
                "description", "due_date", "outcome", "created_at", "updated_at", "community", "origin_session",
                "checkpoint_session");
        assertThat(onlyRow(fragment, "action_item_checkpoint")).containsOnlyKeys("decided_by_user_id", "outcome",
                "decided_at", "action_item", "session");
    }

    /** Each label carries what the person reading the export actually knows the thing by, not the row it came from. */
    @Test
    void eachLabelNamesTheThingItsKeyPointedAt() {

        seedTheWholeModuleFor(TARGET);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(onlyRow(fragment, "topic")).containsEntry("community", COMMUNITY_NAME);
        assertThat(onlyRow(fragment, "topic_vote")).containsEntry("topic", TOPIC_TITLE);
        assertThat(onlyRow(fragment, "session_attendance")).containsEntry("session", SESSION_TITLE);
        assertThat(onlyRow(fragment, "session_attendance").get("session_date")).hasToString(OCCURRENCE.toString());
        assertThat(onlyRow(fragment, "community_session")).containsEntry("community", COMMUNITY_NAME)
                .containsEntry("series", SERIES_TITLE);
        assertThat(onlyRow(fragment, "event_slot_presenter")).containsEntry("slot", SLOT_TITLE).containsEntry("session",
                SESSION_TITLE);
        assertThat(onlyRow(fragment, "calendar_subscription")).containsEntry("community", COMMUNITY_NAME);
        assertThat(onlyRow(fragment, "action_item")).containsEntry("community", COMMUNITY_NAME)
                .containsEntry("origin_session", SESSION_TITLE).containsEntry("checkpoint_session", SESSION_TITLE);
        assertThat(onlyRow(fragment, "action_item_checkpoint")).containsEntry("action_item", ACTION_ITEM_TITLE)
                .containsEntry("session", SESSION_TITLE);
    }

    /**
     * A session of a series is normally called whatever the series is called and stores no title of its own, so the
     * label falls back to the series rather than handing somebody a nameless row.
     */
    @Test
    void aSessionWithNoTitleOfItsOwnIsNamedAfterItsSeries() {

        seedAttendance(TARGET, seedSession(TARGET, null, seedSeries()));

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(onlyRow(fragment, "session_attendance")).containsEntry("session", SERIES_TITLE);
    }

    /**
     * The row is the target's because one of {@code action_item}'s two columns names them; the other names a colleague,
     * and which one that is differs per row. Both columns survive in the projection so the shape of the export does not
     * change with the fixture, and each is emitted only where it names the requesting member. A row pointing at no
     * session keeps its own facts and carries a null label, which is what the left joins are for.
     */
    @Test
    void anActionItemTheMemberSharesWithSomebodyElseNamesOnlyTheirOwnHalfOfIt() {

        seedActionItem("Raised by target", TARGET, NEIGHBOUR);
        seedActionItem("Assigned to target", NEIGHBOUR, TARGET);

        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "action_item");

        assertThat(rowTitled(rows, "Raised by target")).containsEntry("raised_by_user_id", TARGET)
                .containsEntry("assignee_user_id", null).containsEntry("origin_session", null);
        assertThat(rowTitled(rows, "Assigned to target")).containsEntry("assignee_user_id", TARGET)
                .containsEntry("raised_by_user_id", null);
    }

    @Test
    void organizationScopedExportCarriesEveryMembersRows() {

        seedTheWholeModuleFor(TARGET);
        seedTheWholeModuleFor(NEIGHBOUR);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "topic")).hasSize(2);
        assertThat(rowsOf(fragment, "action_item")).hasSize(2);
        assertThat(rowsOf(fragment, "community_session")).hasSize(2);
        assertThat(rowsOf(fragment, "topic_vote")).hasSize(2);
        assertThat(rowsOf(fragment, "session_attendance")).hasSize(2);
        assertThat(rowsOf(fragment, "event_slot_presenter")).hasSize(2);
        assertThat(rowsOf(fragment, "calendar_subscription")).hasSize(2);
        assertThat(rowsOf(fragment, "action_item_checkpoint")).hasSize(2);
        assertThat(json(fragment)).contains(TARGET, NEIGHBOUR);
    }

    /** One row in every table of the module, all of it named after the same person and all of it labelled. */
    private void seedTheWholeModuleFor(String user) {

        var sessionId = seedSession(user, SESSION_TITLE, seedSeries());
        var topicId = seedTopic(TOPIC_TITLE, user);
        var actionItemId = seedActionItem(ACTION_ITEM_TITLE, user, user, sessionId);
        seedVote(user, topicId);
        seedAttendance(user, sessionId);
        seedPresenter(user, seedSlot(sessionId));
        seedSubscription(user, user + "-subscription-hash");
        seedCheckpoint(actionItemId, sessionId, user);
    }

    private static Map<String, Object> onlyRow(ExportFragment fragment, String table) {

        var rows = rowsOf(fragment, table);
        assertThat(rows).as(table).hasSize(1);
        return rows.get(0);
    }

    private static Map<String, Object> rowTitled(List<Map<String, Object>> rows, String title) {

        return rows.stream().filter(row -> title.equals(row.get("title"))).findFirst().orElseThrow();
    }

    private Long seedCommunity() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, "
                        + "updated_at, version) VALUES (:org, 'Exporter Test Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'exporter-test-community', :name, "
                        + ":kindId, '', 'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "name", COMMUNITY_NAME, "kindId", kindId), Long.class);
    }

    private Long seedSectionType() {

        return jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, "
                        + "version) VALUES (:org, 'Exporter Test Section Type', true, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
    }

    private Long seedSeries() {

        return jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, "
                        + "time_zone, status, starts_on, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + ":title, 1, 'WEEK', 'UTC', 'ACTIVE', :startsOn, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", SERIES_TITLE, "startsOn", OCCURRENCE),
                Long.class);
    }

    private Long seedSession(String facilitator) {

        return seedSession(facilitator, SESSION_TITLE, null);
    }

    private Long seedSession(String facilitator, String title, Long seriesId) {

        var params = new MapSqlParameterSource("org", ORG).addValue("communityId", communityId)
                .addValue("scheduledAt", Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")))
                .addValue("facilitator", facilitator).addValue("title", title).addValue("seriesId", seriesId)
                .addValue("occurrenceDate", OCCURRENCE);
        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, series_id, title, occurrence_date, "
                        + "status, scheduled_at, facilitator_user_id, notes, recording_url, has_time, created_at, "
                        + "updated_at, version) VALUES (:org, :communityId, :seriesId, :title, :occurrenceDate, "
                        + "'SCHEDULED', :scheduledAt, :facilitator, '', '', true, now(), now(), 0) RETURNING id",
                params, Long.class);
    }

    private Long seedSlot(Long forSessionId) {

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id, title) VALUES "
                        + "(:sessionId, 0, :sectionTypeId, 'Exporter Test Section') RETURNING id",
                Map.of("sessionId", forSessionId, "sectionTypeId", sectionTypeId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes, title) VALUES "
                        + "(:sectionId, 0, 10, :title) RETURNING id",
                Map.of("sectionId", sectionId, "title", SLOT_TITLE), Long.class);
    }

    private Long seedTopic(String title, String proposer) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, 'REQUEST', "
                        + ":title, '', :proposer, 'PROPOSED', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title, "proposer", proposer), Long.class);
    }

    private Long seedActionItem(String title, String raisedBy, String assignee) {

        return seedActionItem(title, raisedBy, assignee, null);
    }

    private Long seedActionItem(String title, String raisedBy, String assignee, Long sessionId) {

        var params = new MapSqlParameterSource("org", ORG).addValue("communityId", communityId).addValue("title", title)
                .addValue("raisedBy", raisedBy).addValue("assignee", assignee).addValue("sessionId", sessionId);
        return jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, origin_session_id, checkpoint_session_id, outcome, created_at, "
                        + "updated_at, version) VALUES (:org, :communityId, :title, '', :raisedBy, :assignee, "
                        + ":sessionId, :sessionId, 'OPEN', now(), now(), 0) RETURNING id",
                params, Long.class);
    }

    private void seedCheckpoint(Long actionItemId, Long sessionId, String decidedBy) {

        jdbc.update(
                "INSERT INTO action_item_checkpoint (organization_id, action_item_id, session_id, outcome, "
                        + "decided_by_user_id, decided_at) VALUES (:org, :actionItemId, :sessionId, 'CARRIED', "
                        + ":decidedBy, now())",
                Map.of("org", ORG, "actionItemId", actionItemId, "sessionId", sessionId, "decidedBy", decidedBy));
    }

    private void seedVote(String user, Long topicId) {

        jdbc.update(
                "INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version) "
                        + "VALUES (:org, :topicId, :user, now(), now(), 0)",
                Map.of("org", ORG, "topicId", topicId, "user", user));
    }

    private void seedAttendance(String user, Long forSessionId) {

        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, "
                        + "version) VALUES (:org, :sessionId, :user, now(), now(), 0)",
                Map.of("org", ORG, "sessionId", forSessionId, "user", user));
    }

    private void seedPresenter(String user, Long forSlotId) {

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :user)",
                Map.of("slotId", forSlotId, "user", user));
    }

    private void seedSubscription(String user, String tokenHash) {

        jdbc.update(
                "INSERT INTO calendar_subscription (organization_id, community_id, user_id, token_hash, "
                        + "created_at, version) VALUES (:org, :communityId, :user, :tokenHash, now(), 0)",
                Map.of("org", ORG, "communityId", communityId, "user", user, "tokenHash", tokenHash));
    }
}
