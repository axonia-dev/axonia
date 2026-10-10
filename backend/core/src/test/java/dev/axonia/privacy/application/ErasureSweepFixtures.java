package dev.axonia.privacy.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.access.api.OwnerBinding;
import dev.axonia.founding.api.FoundingRequest;
import dev.axonia.founding.api.OrganizationFounder;
import dev.axonia.shared.api.tenancy.OrganizationScope;

/** The layer 4 sweep and the open half of its fixture, shared by {@link ErasureSweepTest} and its Cloud twin. */
public final class ErasureSweepFixtures {

    /** Someone other than the member, who saved content the member authored. */
    static final String OWNER = "sweep-owner";

    private final NamedParameterJdbcTemplate jdbc;

    ErasureSweepFixtures(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    /**
     * An Organization founded through open code, with its reference vocabularies, an Owner outside {@code members}, and
     * one community every member belongs to.
     */
    void found(OrganizationFounder founder, String organizationId, String name, List<String> members) {

        OrganizationScope.runAs(organizationId, () -> founder.found(
                new FoundingRequest(organizationId, name, new OwnerBinding.User("founding-owner", "Founding Owner"))));
        var kindId = jdbc.queryForObject(
                "SELECT id FROM community_kind WHERE organization_id = :org ORDER BY id LIMIT 1",
                Map.of("org", organizationId), Long.class);
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, :slug, :name, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", organizationId, "slug", organizationId + "-community", "name", name, "kindId", kindId),
                Long.class);
        members.forEach(member -> jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :communityId, :user, 'MEMBER', now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "user", member)));
    }

    /**
     * One row per open module's eraser for a member of an Organization that already has one community, and every branch
     * of the events module's time/status/outcome splits. Seeded with direct SQL rather than through every module's own
     * service, because most of these rows (a personal access token, an expertise signal) have no self-serve creation
     * path at all.
     */
    void seedOpenRows(String organizationId, String userId, String name, String email) {

        var communityId = jdbc.queryForObject("SELECT id FROM communities WHERE organization_id = :org",
                Map.of("org", organizationId), Long.class);
        var artifactTypeId = jdbc.queryForObject(
                "SELECT id FROM artifact_type WHERE organization_id = :org ORDER BY id LIMIT 1",
                Map.of("org", organizationId), Long.class);
        var sectionTypeId = jdbc.queryForObject(
                "SELECT id FROM event_section_type WHERE organization_id = :org ORDER BY id LIMIT 1",
                Map.of("org", organizationId), Long.class);

        // identity: the person record itself (deleted last, but seeded first - everything else references it)
        // avatar_media_key embeds the subject inside a longer object key: a denormalised identifier the old
        // equality predicate could never see, and the reason the sweep now matches by substring.
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, scim_id, arrival, "
                + "avatar_media_key, created_at, updated_at, version) VALUES (:org, :user, :name, :email, :scim, "
                + "'SYNCED', :avatarKey, now(), now(), 0)",
                Map.of("org", organizationId, "user", userId, "name", name, "email", email, "scim", scimIdOf(userId),
                        "avatarKey", "avatars/" + organizationId + "/" + userId + "/original.jpg"));

        // identity: a directory group membership, reached through the identity row's scim_id
        var groupId = jdbc
                .queryForObject(
                        "INSERT INTO directory_group (organization_id, scim_id, display_name, reconciliation_key, "
                                + "created_at, updated_at, version) VALUES (:org, 'sweep-group-scim', 'Sweep Group', "
                                + "'sweep-group', now(), now(), 0) RETURNING id",
                        Map.of("org", organizationId), Long.class);
        jdbc.update(
                "INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, "
                        + "updated_at, version) VALUES (:org, :groupId, :scim, now(), now(), 0)",
                Map.of("org", organizationId, "groupId", groupId, "scim", scimIdOf(userId)));

        // identity: a personal access token, and an agent tool call logged against it
        var tokenId = jdbc.queryForObject(
                "INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                        + "created_at, updated_at, version) VALUES (:org, :user, :hash, 'Sweep token', '', now(), "
                        + "now(), 0) RETURNING id",
                Map.of("org", organizationId, "user", userId, "hash", "hash-" + userId), Long.class);
        jdbc.update(
                "INSERT INTO agent_tool_call (organization_id, user_id, token_id, tool_name, outcome, occurred_at) "
                        + "VALUES (:org, :user, :tokenId, 'search', 'SUCCESS', now())",
                Map.of("org", organizationId, "user", userId, "tokenId", tokenId));

        // knowledge: an artifact the target contributed, and an attachment they attached to a past session
        var artifactId = jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, :typeId, 'Sweep Artifact', 'A write-up of the caching session.', "
                        + "'https://example.test', :user, 'PUBLISHED', "
                        + "'Sweep Artifact A write-up of the caching session. caching', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", organizationId, "communityId", communityId, "typeId", artifactTypeId, "user", userId),
                Long.class);
        var pastSessionId = seedSession(organizationId, communityId, userId, "HELD", "now() - interval '30 days'");
        jdbc.update(
                "INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, attached_by_user_id, "
                        + "created_at, updated_at, version) VALUES (:org, :artifactId, :sessionId, :user, now(), "
                        + "now(), 0)",
                Map.of("org", organizationId, "artifactId", artifactId, "sessionId", pastSessionId, "user", userId));

        // recognition: a contribution the target loved (recognized_contribution itself carries no personal
        // column since V4 - contributor credit is derived from the owning bounded context at read time)
        var contributionId = jdbc.queryForObject(
                "INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, "
                        + "community_id, title, occurred_at, created_at, updated_at, version) VALUES (:org, "
                        + "'ARTIFACT', :artifactId, :communityId, 'Sweep Artifact', now(), now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", organizationId, "artifactId", artifactId, "communityId", communityId), Long.class);
        jdbc.update(
                "INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id, "
                        + "created_at, updated_at, version) VALUES (:org, :contributionId, :user, now(), now(), 0)",
                Map.of("org", organizationId, "contributionId", contributionId, "user", userId));

        // bookmarks: the target's own save (deleted) and, as a SEPARATE row, someone else's save of content the
        // target authored (anonymised). One row wearing both hats would let the DELETE remove it before the
        // ANONYMISE UPDATE ever had a row left to affect, hiding that disposition entirely.
        jdbc.update(
                "INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id, "
                        + "source_author_user_id, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'ARTIFACT', :artifactId, :user, :user, now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "artifactId", artifactId, "user", userId));
        jdbc.update(
                "INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id, "
                        + "source_author_user_id, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'ARTIFACT', :artifactId, :owner, :user, now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "artifactId", artifactId, "owner", OWNER,
                        "user", userId));

        // expertise: every signal/curation table keyed on the target
        jdbc.update(
                "INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                        + "source_type, source_id, occurred_at) VALUES (:org, :communityId, :user, 'sweep-tag', "
                        + "'IMPLICIT', 'ARTIFACT', 1, now())",
                Map.of("org", organizationId, "communityId", communityId, "user", userId));
        jdbc.update(
                "INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) VALUES (:org, :user, 'sweep-tag')",
                Map.of("org", organizationId, "user", userId));
        jdbc.update("INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, :user)",
                Map.of("org", organizationId, "user", userId));
        jdbc.update(
                "INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                        + "VALUES (:org, :user, 'ARTIFACT', 1, 'sweep-tag')",
                Map.of("org", organizationId, "user", userId));
        jdbc.update(
                "INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :communityId, :user, 'sweep-tag', now())",
                Map.of("org", organizationId, "communityId", communityId, "user", userId));

        // learning: an area the target wants to learn in the community they joined, which the erasure's leave path
        // deletes before any eraser runs
        jdbc.update(
                "INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                        + "VALUES (:org, :communityId, :user, 'sweep-tag', now())",
                Map.of("org", organizationId, "communityId", communityId, "user", userId));

        // events: a topic still open and one already done, both proposed by the target (anonymised) and one voted
        // on by the target
        var openTopicId = seedTopic(organizationId, communityId, userId, "PROPOSED");
        seedTopic(organizationId, communityId, userId, "DONE");
        jdbc.update(
                "INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version) "
                        + "VALUES (:org, :topicId, :user, now(), now(), 0)",
                Map.of("org", organizationId, "topicId", openTopicId, "user", userId));
        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, "
                        + "version) VALUES (:org, :sessionId, :user, now(), now(), 0)",
                Map.of("org", organizationId, "sessionId", pastSessionId, "user", userId));

        // events: an action item still open (assignee cleared) and one already answered (assignee anonymised as
        // history, exactly like a DONE topic); raised_by is anonymised on both regardless of outcome, and the
        // checkpoint that closed the second names the target as the session-side decider
        seedActionItem(organizationId, communityId, userId, "OPEN");
        var closedActionItemId = seedActionItem(organizationId, communityId, userId, "DONE");
        jdbc.update(
                "INSERT INTO action_item_checkpoint (organization_id, action_item_id, session_id, outcome, "
                        + "decided_by_user_id, decided_at) VALUES (:org, :actionItemId, :sessionId, 'CARRIED', "
                        + ":user, now())",
                Map.of("org", organizationId, "actionItemId", closedActionItemId, "sessionId", pastSessionId, "user",
                        userId));

        // events: a future session (facilitator cleared, presenter deleted) and confirmation the past session
        // (facilitator anonymised, presenter anonymised) above already carries the target as facilitator
        var futureSessionId = seedSession(organizationId, communityId, userId, "SCHEDULED",
                "now() + interval '30 days'");
        seedSlotPresenter(futureSessionId, sectionTypeId, userId);
        seedSlotPresenter(pastSessionId, sectionTypeId, userId);
    }

    /** An artifact in another Organization naming the subject, which no erasure of theirs elsewhere may reach. */
    void seedArtifactNaming(String organizationId, String userId) {

        var communityId = jdbc.queryForObject("SELECT id FROM communities WHERE organization_id = :org",
                Map.of("org", organizationId), Long.class);
        var artifactTypeId = jdbc.queryForObject(
                "SELECT id FROM artifact_type WHERE organization_id = :org ORDER BY id LIMIT 1",
                Map.of("org", organizationId), Long.class);

        jdbc.update("INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                + "contributor_user_id, status, search_document, created_at, updated_at, version) VALUES "
                + "(:org, :communityId, :typeId, 'Other Organization Artifact', '', 'https://example.test', :user, "
                + "'PUBLISHED', '', now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "typeId", artifactTypeId, "user", userId));
    }

    List<String> contributorsIn(String organizationId) {

        return jdbc.queryForList("SELECT contributor_user_id FROM artifact WHERE organization_id = :org",
                Map.of("org", organizationId), String.class);
    }

    /**
     * Matches by substring, not equality. A name embedded in longer free text - {@code artifact.search_document},
     * {@code community_session.notes}, {@code topic.description} - is invisible to {@code = :needle}, and those are
     * exactly the columns a member's own words end up in. {@code position} rather than {@code LIKE} so a needle is
     * never read as a wildcard pattern.
     */
    List<String> hitsFor(List<String> needles) {

        var hits = new ArrayList<String>();
        for (var column : everyTextColumn()) {
            for (var needle : needles) {
                var found = jdbc.queryForObject("SELECT count(*) FROM %s WHERE position(:needle in %s) > 0"
                        .formatted(column.table(), column.name()), Map.of("needle", needle), Long.class);
                if (found > 0) {
                    hits.add(column.table() + "." + column.name() + " still holds '" + needle + "'");
                }
            }
        }
        return hits;
    }

    /** Every character-typed column in the public schema, discovered rather than listed. */
    private List<Column> everyTextColumn() {

        return jdbc.query("""
                SELECT table_name, column_name FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND data_type IN ('character varying', 'text', 'character')
                """, (rs, i) -> new Column(rs.getString(1), rs.getString(2)));
    }

    static String scimIdOf(String userId) {
        return "scim-" + userId;
    }

    private long seedSession(String organizationId, long communityId, String userId, String status,
            String scheduledAtSql) {

        return jdbc.queryForObject(
                ("INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "facilitator_user_id, notes, recording_url, has_time, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, :status, %s, :user, 'We agreed to revisit the retry policy.', '', "
                        + "true, now(), now(), 0) RETURNING id").formatted(scheduledAtSql),
                Map.of("org", organizationId, "communityId", communityId, "status", status, "user", userId),
                Long.class);
    }

    private long seedTopic(String organizationId, long communityId, String userId, String status) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'REQUEST', 'Sweep Topic', 'Write up what we decided about retries.', :user, :status, "
                        + "now(), now(), 0) RETURNING id",
                Map.of("org", organizationId, "communityId", communityId, "user", userId, "status", status),
                Long.class);
    }

    private long seedActionItem(String organizationId, long communityId, String userId, String outcome) {

        return jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, outcome, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'Sweep Action Item', 'Write up what we decided about retries.', :user, :user, :outcome, "
                        + "now(), now(), 0) RETURNING id",
                Map.of("org", organizationId, "communityId", communityId, "user", userId, "outcome", outcome),
                Long.class);
    }

    private void seedSlotPresenter(long sessionId, long sectionTypeId, String userId) {

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id) VALUES (:sessionId, 1, "
                        + ":sectionTypeId) RETURNING id",
                Map.of("sessionId", sessionId, "sectionTypeId", sectionTypeId), Long.class);
        var slotId = jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes) VALUES (:sectionId, 1, 10) "
                        + "RETURNING id",
                Map.of("sectionId", sectionId), Long.class);
        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, :user)",
                Map.of("slotId", slotId, "user", userId));
    }

    static void authenticateAs(String subject) {
        var jwt = Jwt.withTokenValue("token-" + subject).header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    static void authenticateAsInOrg(String subject, String organizationId) {
        var jwt = Jwt.withTokenValue("token-" + subject + "-" + organizationId).header("alg", "none").subject(subject)
                .claim("org", organizationId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private record Column(String table, String name) {
    }
}
