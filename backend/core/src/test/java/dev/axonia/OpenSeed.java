package dev.axonia;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * One row in every open table any {@link PersonalDataExporter} reads from, for layer 3 of the data-subject-rights gate.
 * No attention is paid to which columns are null: {@code SELECT *} carries every column name as a map key regardless of
 * its value, so a personal column only needs a row to exist in its table, not a non-null value in it.
 */
public final class OpenSeed {

    public static final String ORG = "layer3-org";

    public static final String USER = "layer3-user";

    private final NamedParameterJdbcTemplate jdbc;

    public OpenSeed(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    public void seedOpenTables() {

        var communityId = seedCommunity();
        var sectionTypeId = seedSectionType();
        var sessionId = seedSession(communityId);
        var slotId = seedSlot(sessionId, sectionTypeId);
        var topicId = seedTopic(communityId);
        var roleId = seedRole();
        var groupId = seedDirectoryGroup();
        var contributionId = seedContribution();
        var artifactId = seedArtifact();

        jdbc.update("""
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES (:org, :communityId, :artifactId, 'layer3-user', DATE '2026-04-15', now())
                """, Map.of("org", ORG, "communityId", communityId, "artifactId", artifactId));

        seedRoleBinding(roleId);
        jdbc.update(
                "INSERT INTO agent_tool_call (organization_id, user_id, token_id, tool_name, outcome, "
                        + "occurred_at) VALUES (:org, 'layer3-user', 1, 'layer3-tool', 'SUCCESS', now())",
                Map.of("org", ORG));
        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, member_user_id, source_author_user_id,
                                      saveable_type, saveable_id, created_at, updated_at, version)
                VALUES (:org, :communityId, 'layer3-user', 'layer3-user', 'ARTIFACT', 1, now(), now(), 0)
                """, Map.of("org", ORG, "communityId", communityId));
        jdbc.update(
                "INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                        + "version) VALUES (:org, :communityId, 'layer3-user', 'MEMBER', now(), now(), 0)",
                Map.of("org", ORG, "communityId", communityId));
        jdbc.update(
                "INSERT INTO topic_vote (organization_id, topic_id, user_id, created_at, updated_at, version) "
                        + "VALUES (:org, :topicId, 'layer3-user', now(), now(), 0)",
                Map.of("org", ORG, "topicId", topicId));
        jdbc.update(
                "INSERT INTO session_attendance (organization_id, session_id, user_id, created_at, updated_at, "
                        + "version) VALUES (:org, :sessionId, 'layer3-user', now(), now(), 0)",
                Map.of("org", ORG, "sessionId", sessionId));
        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, 'layer3-user')",
                Map.of("slotId", slotId));
        var actionItemId = jdbc.queryForObject(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, origin_session_id, outcome, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, 'Layer 3 Action Item', '', 'layer3-user', 'layer3-user', "
                        + ":sessionId, 'OPEN', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "communityId", communityId, "sessionId", sessionId), Long.class);
        jdbc.update(
                "INSERT INTO action_item_checkpoint (organization_id, action_item_id, session_id, outcome, "
                        + "decided_by_user_id, decided_at) VALUES (:org, :actionItemId, :sessionId, 'CARRIED', "
                        + "'layer3-user', now())",
                Map.of("org", ORG, "actionItemId", actionItemId, "sessionId", sessionId));
        jdbc.update(
                "INSERT INTO calendar_subscription (organization_id, community_id, user_id, token_hash, "
                        + "created_at, version) VALUES (:org, :communityId, 'layer3-user', "
                        + "'layer3-calendar-subscription-hash', now(), 0)",
                Map.of("org", ORG, "communityId", communityId));
        seedExpertiseGraph();
        jdbc.update("INSERT INTO learning_intent (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, 'layer3-user', 'layer3-tag', now())", Map.of("org", ORG));
        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival, "
                + "avatar_media_key, avatar_override_key, created_at, updated_at, version) "
                + "VALUES (:org, 'layer3-user', 'Layer 3 User', 'layer3-scim-id', 'SYNCED', "
                + "'avatars/layer3/user.jpg', 'avatars/layer3/own.png', now(), now(), 0)", Map.of("org", ORG));
        jdbc.update("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                + "created_at, updated_at, version) VALUES (:org, 'layer3-user', 'layer3-pat-hash', 'Layer 3 PAT', "
                + "'read', now(), now(), 0)", Map.of("org", ORG));
        jdbc.update("INSERT INTO agent_connection (organization_id, user_id, client_id, client_name, client_host, "
                + "scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at, updated_at, "
                + "version) VALUES (:org, 'layer3-user', 'https://claude.ai/cc', 'Claude Code', 'claude.ai', 'read', "
                + "'https://axonia.test/mcp', 'http://localhost/callback', 'layer3-challenge', now(), "
                + "now() + interval '1 day', now(), now(), 0)", Map.of("org", ORG));
        jdbc.update(
                "INSERT INTO directory_group_member (organization_id, group_id, user_scim_id, created_at, "
                        + "updated_at, version) VALUES (:org, :groupId, 'layer3-scim-id', now(), now(), 0)",
                Map.of("org", ORG, "groupId", groupId));
        jdbc.update("INSERT INTO artifact_attachment (organization_id, artifact_id, session_id, segment_id, "
                + "attached_by_user_id, created_at, updated_at, version) VALUES (:org, :artifactId, 1, 1, "
                + "'layer3-user', now(), now(), 0)", Map.of("org", ORG, "artifactId", artifactId));
        jdbc.update("INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id, "
                + "created_at, updated_at, version) VALUES (:org, :contributionId, 'layer3-user', now(), "
                + "now(), 0)", Map.of("org", ORG, "contributionId", contributionId));
        jdbc.update("""
                INSERT INTO contribution_reaction (organization_id, community_id, contribution_type,
                    contribution_id, reactor_user_id, emoji, created_at, updated_at)
                VALUES (:org, 1, 'SLOT_PRESENTED', 1, :user, '🧠', now(), now())
                """, Map.of("org", ORG, "user", USER));
        jdbc.update("""
                INSERT INTO reaction_definition (organization_id, name, image_key, state,
                    proposed_by_user_id, approved_by_user_id, created_at, updated_at)
                VALUES (:org, 'ship-it', 'k', 'APPROVED', :user, :user, now(), now())
                """, Map.of("org", ORG, "user", USER));
        jdbc.update("INSERT INTO communication_preference (organization_id, user_id, message_type, enabled, "
                + "created_at, updated_at, version) VALUES (:org, 'layer3-user', 'session_upcoming', true, now(), "
                + "now(), 0)", Map.of("org", ORG));
        var messageId = jdbc.queryForObject(
                "INSERT INTO communication_message (organization_id, message_type, subject_ref, template, facts, "
                        + "created_at, updated_at, version) VALUES (:org, 'session_upcoming', 'layer3-session', "
                        + "'session-template', '{}'::jsonb, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        jdbc.update(
                "INSERT INTO communication_delivery (organization_id, message_id, user_id, status, created_at, "
                        + "updated_at, version) VALUES (:org, :message, 'layer3-user', 'PENDING', now(), now(), 0)",
                Map.of("org", ORG, "message", messageId));
        jdbc.update("INSERT INTO communication_expired_message (organization_id, message_type, subject_ref, "
                + "pending_count, sent_count, suppressed_count, unreachable_count, abandoned_count, expired_at) "
                + "VALUES (:org, 'session_wrap_up', 'layer3-expired-subject', 0, 1, 0, 0, 0, now())",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO roundup_settings (organization_id, enabled, created_at, updated_at, version) "
                + "VALUES (:org, true, now(), now(), 0)", Map.of("org", ORG));
        seedOnboarding();
    }

    /**
     * The person axis: these two tables carry no organization_id at all - a path is rooted at a person and outlives any
     * one hub - so they are seeded on the same layer3-user and on nothing else. This is the personal-data layers' third
     * registration point.
     */
    private void seedOnboarding() {

        jdbc.update("INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id, "
                + "anchor_community_id, closed_at, created_at, updated_at) VALUES ('layer3-user', 'MEMBER', "
                + "'layer3-community', 'layer3-org', 1, NULL, now(), now())", Map.of());
        jdbc.update("INSERT INTO onboarding_step (subject, ordinal, step_key, route, detail, visited_at) VALUES "
                + "('layer3-user', 0, 'DECLARE_EXPERTISE', '/communities/layer3-community/me/expertise', NULL, "
                + "NULL)", Map.of());
    }

    private Long seedCommunity() {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, "
                        + "updated_at, version) VALUES (:org, 'Layer 3 Guild', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, "
                        + "updated_at, version) VALUES (:org, 'layer3-community', 'Layer 3 Community', :kindId, '', "
                        + "'ACTIVE', now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "kindId", kindId), Long.class);
    }

    private Long seedSectionType() {

        return jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, "
                        + "version) VALUES (:org, 'Layer 3 Section Type', true, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
    }

    private Long seedSession(Long communityId) {

        return jdbc
                .queryForObject("INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "facilitator_user_id, notes, recording_url, has_time, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, 'SCHEDULED', now(), 'layer3-user', '', '', true, now(), now(), 0) "
                        + "RETURNING id", Map.of("org", ORG, "communityId", communityId), Long.class);
    }

    private Long seedSlot(Long sessionId, Long sectionTypeId) {

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, \"position\", section_type_id, title) VALUES "
                        + "(:sessionId, 0, :sectionTypeId, 'Layer 3 Section') RETURNING id",
                Map.of("sessionId", sessionId, "sectionTypeId", sectionTypeId), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, \"position\", duration_minutes, title) VALUES "
                        + "(:sectionId, 0, 10, 'Layer 3 Slot') RETURNING id",
                Map.of("sectionId", sectionId), Long.class);
    }

    private Long seedTopic(Long communityId) {

        return jdbc.queryForObject(
                "INSERT INTO topic (organization_id, community_id, kind, title, description, proposer_user_id, "
                        + "status, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'REQUEST', 'Layer 3 Topic', '', 'layer3-user', 'PROPOSED', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG, "communityId", communityId), Long.class);
    }

    private Long seedRole() {

        return jdbc.queryForObject("INSERT INTO role (organization_id, name, description, created_at, updated_at, "
                + "version) VALUES (:org, 'layer3-role', 'seeded for the layer 3 export gate', now(), now(), 0) "
                + "RETURNING id", Map.of("org", ORG), Long.class);
    }

    private void seedRoleBinding(Long roleId) {

        jdbc.update("INSERT INTO role_binding (organization_id, subject_type, subject_id, role_id, subject_label, "
                + "created_at, updated_at, version) VALUES (:org, 'USER', 'layer3-user', :roleId, "
                + "'Layer 3 Subject', now(), now(), 0)", Map.of("org", ORG, "roleId", roleId));
    }

    private Long seedDirectoryGroup() {

        return jdbc.queryForObject("INSERT INTO directory_group (organization_id, scim_id, external_id, display_name, "
                + "reconciliation_key, created_at, updated_at, version) VALUES (:org, "
                + "'layer3-group-scim-id', 'layer3-group-external-id', 'Layer 3 Group', "
                + "'layer3-reconciliation-key', now(), now(), 0) RETURNING id", Map.of("org", ORG), Long.class);
    }

    private void seedExpertiseGraph() {

        jdbc.update("INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                + "source_type, source_id, occurred_at) VALUES (:org, 1, 'layer3-user', 'layer3-tag', 'CONTRIBUTED', "
                + "'ARTIFACT', 1, now())", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) VALUES (:org, 'layer3-user', "
                + "'layer3-tag')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, 'layer3-user')",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                + "VALUES (:org, 'layer3-user', 'ARTIFACT', 1, 'layer3-tag')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, 'layer3-user', 'layer3-tag', now())", Map.of("org", ORG));
    }

    private Long seedContribution() {

        return jdbc.queryForObject(
                "INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, "
                        + "community_id, title, occurred_at, created_at, updated_at, version) "
                        + "VALUES (:org, 'ARTIFACT', 1, 1, 'Layer 3 Contribution', now(), now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", ORG), Long.class);
    }

    private Long seedArtifact() {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) "
                        + "VALUES (:org, 1, 1, 'Layer 3 Artifact', '', 'https://example.test/artifact', "
                        + "'layer3-user', 'PUBLISHED', 'layer 3 artifact', now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
    }
}
