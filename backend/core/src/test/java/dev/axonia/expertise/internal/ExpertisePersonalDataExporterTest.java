package dev.axonia.expertise.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertisePersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private ExpertisePersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesTheMembersOwnGraphAcrossAllFiveTables() {

        var communityId = seedCommunity("Target Community");
        seedExpertiseGraph(TARGET, "target-tag", communityId);
        seedExpertiseGraph(NEIGHBOUR, "neighbour-tag", communityId);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("expertise.json");
        assertThat(json(fragment)).contains("target-tag").doesNotContain("neighbour-tag");

        assertThat(rowsOf(fragment, "expertise_signal")).singleElement().satisfies(row -> assertThat(row)
                .containsOnlyKeys("user_id", "tag", "signal_type", "source_type", "occurred_at", "community", "source")
                .containsEntry("tag", "target-tag").containsEntry("community", "Target Community"));
        assertThat(rowsOf(fragment, "expertise_hidden_tag")).singleElement().satisfies(
                row -> assertThat(row).containsOnlyKeys("user_id", "tag").containsEntry("tag", "target-tag"));
        assertThat(rowsOf(fragment, "expertise_opt_out")).singleElement()
                .satisfies(row -> assertThat(row).containsOnlyKeys("user_id").containsEntry("user_id", TARGET));
        assertThat(rowsOf(fragment, "expertise_dismissed_signal")).singleElement().satisfies(row -> assertThat(row)
                .containsOnlyKeys("user_id", "source_type", "tag", "source").containsEntry("tag", "target-tag"));
        assertThat(rowsOf(fragment, "expertise_declaration")).singleElement()
                .satisfies(row -> assertThat(row).containsOnlyKeys("user_id", "tag", "declared_at", "community")
                        .containsEntry("tag", "target-tag").containsEntry("community", "Target Community"));
    }

    @Test
    void organizationScopedExportCarriesEveryMembersGraph() {

        var communityId = seedCommunity("Org Community");
        seedExpertiseGraph(TARGET, "target-tag", communityId);
        seedExpertiseGraph(NEIGHBOUR, "neighbour-tag", communityId);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(json(fragment)).contains("target-tag").contains("neighbour-tag");
        assertThat(rowsOf(fragment, "expertise_signal")).hasSize(2);
        assertThat(rowsOf(fragment, "expertise_hidden_tag")).hasSize(2);
        assertThat(rowsOf(fragment, "expertise_opt_out")).hasSize(2);
        assertThat(rowsOf(fragment, "expertise_dismissed_signal")).hasSize(2);
        assertThat(rowsOf(fragment, "expertise_declaration")).hasSize(2);
    }

    /**
     * expertise_signal resolves its polymorphic source through a four-way discriminated join, and a missed SourceType
     * branch fails silently as a null label rather than as a build error, so all four values are exercised here, each
     * asserted against the exact title of the row it points at.
     */
    @Test
    void expertiseSignalResolvesEachSourceTypeToItsOwnTitle() {

        var communityId = seedCommunity("Source Community");
        var sectionTypeId = seedSectionType();
        var sessionId = seedSession(communityId);
        var slotId = seedSlot(sessionId, sectionTypeId, "Layer 3 Slot");
        var artifactId = seedArtifact(communityId, "Layer 3 Artifact");
        var topicId = seedTopic(communityId, "Layer 3 Topic");
        var actionItemId = seedActionItem(communityId, "Layer 3 Action Item");

        seedSignal(TARGET, "slot-tag", "SLOT", slotId, communityId);
        seedSignal(TARGET, "artifact-tag", "ARTIFACT", artifactId, communityId);
        seedSignal(TARGET, "topic-tag", "TOPIC", topicId, communityId);
        seedSignal(TARGET, "action-item-tag", "ACTION_ITEM", actionItemId, communityId);

        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "expertise_signal");

        assertThat(rows).hasSize(4);
        assertSource(rows, "slot-tag", "Layer 3 Slot");
        assertSource(rows, "artifact-tag", "Layer 3 Artifact");
        assertSource(rows, "topic-tag", "Layer 3 Topic");
        assertSource(rows, "action-item-tag", "Layer 3 Action Item");
    }

    /** A source whose row has been deleted keeps the signal itself and carries a null label, never vanishes. */
    @Test
    void expertiseSignalKeepsItsOwnRowWithANullSourceWhenTheTargetIsGone() {

        var communityId = seedCommunity("Orphan Source Community");
        seedSignal(TARGET, "orphan-tag", "ARTIFACT", 999_999L, communityId);

        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "expertise_signal");

        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("source", null));
    }

    /** expertise_dismissed_signal shares the same four-way source resolution as expertise_signal. */
    @Test
    void expertiseDismissedSignalResolvesItsSourceTheSameWay() {

        var communityId = seedCommunity("Dismissed Source Community");
        var topicId = seedTopic(communityId, "Layer 3 Dismissed Topic");
        seedDismissedSignal(TARGET, "dismissed-tag", "TOPIC", topicId);

        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "expertise_dismissed_signal");

        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("source", "Layer 3 Dismissed Topic"));
    }

    /** A declaration whose community has been deleted keeps the declaration itself and carries a null label. */
    @Test
    void expertiseDeclarationCarriesANullCommunityWhenTheCommunityIsGone() {

        seedDeclaration(TARGET, "orphan-community-tag", 999_999L);

        var rows = rowsOf(exporter.export(new ExportScope.OneMember(ORG, TARGET)), "expertise_declaration");

        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("community", null));
    }

    private void assertSource(List<Map<String, Object>> rows, String tag, String expectedSource) {

        assertThat(rows).filteredOn(row -> tag.equals(row.get("tag"))).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("source", expectedSource));
    }

    private void seedExpertiseGraph(String userId, String tag, Long communityId) {

        seedSignal(userId, tag, "ARTIFACT", 1L, communityId);
        seedHiddenTag(userId, tag);
        seedOptOut(userId);
        seedDismissedSignal(userId, tag, "ARTIFACT", 1L);
        seedDeclaration(userId, tag, communityId);
    }

    private void seedSignal(String userId, String tag, String sourceType, Long sourceId, Long communityId) {

        jdbc.update(
                "INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                        + "source_type, source_id, occurred_at) VALUES (:org, :communityId, :user, :tag, "
                        + "'CONTRIBUTED', :sourceType, :sourceId, now())",
                Map.of("org", ORG, "communityId", communityId, "user", userId, "tag", tag, "sourceType", sourceType,
                        "sourceId", sourceId));
    }

    private void seedHiddenTag(String userId, String tag) {

        jdbc.update("INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) VALUES (:org, :user, :tag)",
                Map.of("org", ORG, "user", userId, "tag", tag));
    }

    private void seedOptOut(String userId) {

        jdbc.update("INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, :user)",
                Map.of("org", ORG, "user", userId));
    }

    private void seedDismissedSignal(String userId, String tag, String sourceType, Long sourceId) {

        jdbc.update(
                "INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                        + "VALUES (:org, :user, :sourceType, :sourceId, :tag)",
                Map.of("org", ORG, "user", userId, "sourceType", sourceType, "sourceId", sourceId, "tag", tag));
    }

    private void seedDeclaration(String userId, String tag, Long communityId) {

        jdbc.update(
                "INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :communityId, :user, :tag, now())",
                Map.of("org", ORG, "communityId", communityId, "user", userId, "tag", tag));
    }

    private Long seedCommunity(String name) {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) VALUES "
                        + "(:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "label", name + " Kind"), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :name, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "slug", slug(name), "name", name, "kindId", kindId), Long.class);
    }

    private Long seedSectionType() {

        return jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, "
                        + "version) VALUES (:org, 'Expertise Section Type', true, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
    }

    private Long seedSession(Long communityId) {

        return jdbc
                .queryForObject("INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "facilitator_user_id, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES (:org, :communityId, 'SCHEDULED', now(), 'facilitator-user', '', '', true, "
                        + "now(), now(), 0) RETURNING id", Map.of("org", ORG, "communityId", communityId), Long.class);
    }

    private Long seedSlot(Long sessionId, Long sectionTypeId, String title) {

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id, title) VALUES "
                        + "(:sessionId, 0, :sectionTypeId, 'Expertise Section') RETURNING id",
                Map.of("sessionId", sessionId, "sectionTypeId", sectionTypeId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes, title) VALUES "
                        + "(:sectionId, 0, 10, :title) RETURNING id",
                Map.of("sectionId", sectionId, "title", title), Long.class);
    }

    private Long seedArtifact(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, 1, :title, '', 'https://example.test/artifact', 'contributor-user', "
                        + "'PUBLISHED', 'search', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }

    private Long seedTopic(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, 'REQUEST', :title, "
                        + "'', 'proposer-user', 'PROPOSED', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }

    private Long seedActionItem(Long communityId, String title) {

        return jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "outcome, created_at, updated_at, version) VALUES (:org, :communityId, :title, '', "
                        + "'raiser-user', 'OPEN', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "title", title), Long.class);
    }

    private static String slug(String name) {

        return name.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
