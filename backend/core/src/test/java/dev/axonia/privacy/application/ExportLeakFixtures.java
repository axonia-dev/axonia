package dev.axonia.privacy.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.shared.api.personaldata.ExportScope;

/**
 * The export package as a member receives it, and the open half of the fixture, shared by {@link ExportLeakTest} and
 * its Cloud twin.
 */
final class ExportLeakFixtures {

    /**
     * The subjects are opaque the way a real subject is, and deliberately share no substring with the names and
     * addresses beside them. An earlier revision used {@code leak-target}, which is a prefix of
     * {@code leak-target@example.test}, so the leak assertion below matched inside the member's own email address and
     * reported a leak that was not there. A fixture whose needles nest inside each other cannot tell the two apart.
     */
    static final String TARGET = "01m0zq8h5v4k2n7r3t6w9x1c4b";
    static final String TARGET_NAME = "Leak Target";
    static final String TARGET_EMAIL = "leak-target@example.test";
    static final String NEIGHBOUR = "01m0zq8h5v4k2n7r3t6w9x1d7f";
    static final String NEIGHBOUR_NAME = "Colleague Nextdoor";
    static final String NEIGHBOUR_EMAIL = "colleague@example.test";
    static final String NEIGHBOUR_SCIM_ID = "directory-id-7f31c";
    private final NamedParameterJdbcTemplate jdbc;

    private final DataExport dataExport;

    ExportLeakFixtures(NamedParameterJdbcTemplate jdbc, DataExport dataExport) {

        this.jdbc = jdbc;
        this.dataExport = dataExport;
    }

    /**
     * Two members of one Organization, co-named on the rows where a leak is possible at all: a bookmark whose author is
     * the other one, and action items where proposer and assignee are different people. Plus the target's own
     * credential, whose digest no export may carry.
     */
    void seedTwoMembers(String organizationId, String patHash) {

        var communityId = jdbc.queryForObject("SELECT id FROM communities WHERE organization_id = :org",
                Map.of("org", organizationId), Long.class);
        var artifactTypeId = jdbc.queryForObject(
                "SELECT id FROM artifact_type WHERE organization_id = :org ORDER BY id LIMIT 1",
                Map.of("org", organizationId), Long.class);

        seedIdentityUser(organizationId, TARGET, TARGET_NAME, TARGET_EMAIL, "directory-id-1a04");
        seedIdentityUser(organizationId, NEIGHBOUR, NEIGHBOUR_NAME, NEIGHBOUR_EMAIL, NEIGHBOUR_SCIM_ID);

        var neighboursArtifact = seedArtifact(organizationId, communityId, artifactTypeId, NEIGHBOUR);
        var targetsArtifact = seedArtifact(organizationId, communityId, artifactTypeId, TARGET);
        // the target saved the neighbour's artifact: their own row, naming the neighbour as its author
        seedBookmark(organizationId, communityId, TARGET, NEIGHBOUR, neighboursArtifact);
        // and the neighbour saved the target's: the mirror image, which must not reach the target's package either
        seedBookmark(organizationId, communityId, NEIGHBOUR, TARGET, targetsArtifact);

        // action items the two share, one each way round
        seedActionItem(organizationId, communityId, NEIGHBOUR, TARGET);
        seedActionItem(organizationId, communityId, TARGET, NEIGHBOUR);

        seedMentoring(organizationId, communityId);

        seedPersonalAccessToken(organizationId, TARGET, patHash);
    }

    /**
     * A pairing the two share, the neighbour mentoring the target, with the target's proposal that started it, a note
     * from each side, the neighbour's naming a topic, a next meeting the neighbour set, a pointer naming the neighbour
     * and the target's words: every mentoring row names both of them.
     */
    private void seedMentoring(String organizationId, Long communityId) {

        var params = Map.<String, Object>of("org", organizationId, "communityId", communityId, "target", TARGET,
                "neighbour", NEIGHBOUR);
        jdbc.update("INSERT INTO mentoring_proposal (organization_id, community_id, mentor_user_id, mentee_user_id, "
                + "proposed_by, topics, message, status, created_at, answered_at) VALUES (:org, :communityId, "
                + ":neighbour, :target, 'MENTEE', ARRAY['Retries']::varchar[], 'Could we talk?', 'ACCEPTED', now(), "
                + "now())", params);
        var pairingId = jdbc.queryForObject("INSERT INTO mentoring_pairing (organization_id, community_id, "
                + "mentor_user_id, mentee_user_id, started_at) VALUES (:org, :communityId, :neighbour, :target, now()) "
                + "RETURNING id", params, Long.class);
        var pairing = Map.<String, Object>of("org", organizationId, "pairingId", pairingId, "target", TARGET,
                "neighbour", NEIGHBOUR);
        var topicId = jdbc.queryForObject("INSERT INTO mentoring_topic (organization_id, pairing_id, label, state, "
                + "\"position\", created_at) VALUES (:org, :pairingId, 'Retries', 'TODO', 1, now()) RETURNING id",
                pairing, Long.class);
        jdbc.update(
                "INSERT INTO mentoring_note (organization_id, pairing_id, author_user_id, body, meeting_on, "
                        + "written_at) VALUES (:org, :pairingId, :target, 'Read the policy.', current_date, now())",
                pairing);
        var neighbourNote = jdbc.queryForObject("INSERT INTO mentoring_note (organization_id, pairing_id, "
                + "author_user_id, body, meeting_on, written_at) VALUES (:org, :pairingId, :neighbour, 'Backoff next.', "
                + "current_date, now()) RETURNING id", pairing, Long.class);
        jdbc.update("INSERT INTO mentoring_note_topic (organization_id, note_id, topic_id) VALUES (:org, :noteId, "
                + ":topicId)", Map.of("org", organizationId, "noteId", neighbourNote, "topicId", topicId));
        var meetingId = jdbc.queryForObject("INSERT INTO mentoring_meeting (organization_id, pairing_id, meeting_on, "
                + "planned_by, planned_at) VALUES (:org, :pairingId, current_date + 3, :neighbour, now()) RETURNING id",
                pairing, Long.class);
        jdbc.update(
                "INSERT INTO mentoring_meeting_topic (organization_id, meeting_id, topic_id) VALUES (:org, "
                        + ":meetingId, :topicId)",
                Map.of("org", organizationId, "meetingId", meetingId, "topicId", topicId));
        jdbc.update(
                "UPDATE mentoring_pairing SET next_meeting_on = current_date + 3, next_meeting_set_by = :neighbour, "
                        + "next_meeting_set_at = now() WHERE id = :pairingId",
                pairing);
        jdbc.update(
                "INSERT INTO mentoring_pointer (organization_id, pairing_id, topic_id, kind, target_user_id, "
                        + "created_at) VALUES (:org, :pairingId, :topicId, 'MEMBER', :neighbour, now())",
                Map.of("org", organizationId, "pairingId", pairingId, "topicId", topicId, "neighbour", NEIGHBOUR));
        jdbc.update("INSERT INTO mentoring_words (pairing_id, organization_id, body, written_at) "
                + "VALUES (:pairingId, :org, 'Patient and clear.', now())", pairing);
    }

    /** Every entry of the real archive, decompressed and concatenated: JSON fragments, the manifest and the index. */
    String packageFor(ExportScope scope) {

        return String.join("", entriesFor(scope).values());
    }

    /** Every entry of the real archive, by name, decompressed. */
    Map<String, String> entriesFor(ExportScope scope) {

        var bytes = new ByteArrayOutputStream();
        try {
            dataExport.writeTo(scope, bytes);
            var entries = new LinkedHashMap<String, String>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException("export package could not be assembled", e);
        }
    }

    /**
     * The avatar key matters as much as the columns. A real key is {@code avatars/<organizationId>/<uuid>.<ext>}, so it
     * carries the tenant identifier, and it reaches the archive by a path no column-level check looks at: the ZIP entry
     * name and the {@code files} list in {@code manifest.json}. Seeding one is what makes the assertion above cover
     * that path at all - without it no media entry is ever planned and the whole branch is untested.
     */
    private void seedIdentityUser(String organizationId, String userId, String name, String email, String scimId) {

        jdbc.update("INSERT INTO identity_user (organization_id, user_id, display_name, email, scim_id, "
                + "avatar_media_key, arrival, created_at, updated_at, version) VALUES (:org, :user, :name, :email, "
                + ":scim, :media, 'SYNCED', now(), now(), 0)",
                Map.of("org", organizationId, "user", userId, "name", name, "email", email, "scim", scimId, "media",
                        "avatars/" + organizationId + "/" + userId + "-portrait.jpg"));
    }

    private Long seedArtifact(String organizationId, Long communityId, Long typeId, String contributor) {

        return jdbc.queryForObject(
                "INSERT INTO artifact (organization_id, community_id, type_id, title, description, url, "
                        + "contributor_user_id, status, search_document, created_at, updated_at, version) VALUES "
                        + "(:org, :communityId, :typeId, 'Leak Artifact', '', 'https://example.test', :user, "
                        + "'PUBLISHED', '', now(), now(), 0) RETURNING id",
                Map.of("org", organizationId, "communityId", communityId, "typeId", typeId, "user", contributor),
                Long.class);
    }

    private void seedBookmark(String organizationId, Long communityId, String member, String author, Long artifactId) {

        jdbc.update(
                "INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id, "
                        + "source_author_user_id, created_at, updated_at, version) VALUES (:org, :communityId, "
                        + "'ARTIFACT', :artifactId, :member, :author, now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "artifactId", artifactId, "member", member,
                        "author", author));
    }

    /**
     * {@code due_date} is a real {@code date} column, not a {@code Timestamp}: every export this test drives builds its
     * {@code index.html} against a real {@link java.sql.Date} cell through the full package writer, which is the
     * regression the unit-level test alone missed.
     */
    private void seedActionItem(String organizationId, Long communityId, String raisedBy, String assignee) {

        jdbc.update(
                "INSERT INTO action_item (organization_id, community_id, title, description, raised_by_user_id, "
                        + "assignee_user_id, due_date, outcome, created_at, updated_at, version) VALUES (:org, "
                        + ":communityId, 'Leak Action Item', '', :raisedBy, :assignee, DATE '2026-08-01', 'OPEN', "
                        + "now(), now(), 0)",
                Map.of("org", organizationId, "communityId", communityId, "raisedBy", raisedBy, "assignee", assignee));
    }

    private void seedPersonalAccessToken(String organizationId, String userId, String patHash) {

        jdbc.update("INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, scopes, "
                + "created_at, updated_at, version) VALUES (:org, :user, :hash, 'Leak token', '', now(), now(), 0)",
                Map.of("org", organizationId, "user", userId, "hash", patHash));
    }
}
