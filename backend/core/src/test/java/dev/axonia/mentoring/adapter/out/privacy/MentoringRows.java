package dev.axonia.mentoring.adapter.out.privacy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Plain SQL seeding of the mentoring tables for the privacy adapters' tests, one organization at a time. */
final class MentoringRows {

    private final NamedParameterJdbcTemplate jdbc;
    private final String org;

    MentoringRows(NamedParameterJdbcTemplate jdbc, String org) {

        this.jdbc = jdbc;
        this.org = org;
    }

    long community(String name) {

        var kindId = jdbc.queryForObject(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, "
                        + "version) VALUES (:org, :label, now(), now(), 0) RETURNING id",
                Map.of("org", org, "label", name + " Kind"), Long.class);
        return jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, "
                        + "created_at, updated_at, version) VALUES (:org, :slug, :name, :kindId, '', 'ACTIVE', now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", org, "slug", name.toLowerCase(Locale.ROOT).replace(' ', '-'), "name", name, "kindId",
                        kindId),
                Long.class);
    }

    void availability(long communityId, String userId) {

        jdbc.update("INSERT INTO mentoring_availability (organization_id, community_id, user_id, taking_mentees, "
                + "mentee_limit, looking_for_mentor, updated_at) VALUES (:org, :communityId, :user, true, 3, false, "
                + "now())", Map.of("org", org, "communityId", communityId, "user", userId));
    }

    /** A proposal its mentee sent, so the mentor received it. */
    void proposal(long communityId, String mentorId, String menteeId, String message) {

        jdbc.update("INSERT INTO mentoring_proposal (organization_id, community_id, mentor_user_id, mentee_user_id, "
                + "proposed_by, topics, message, status, created_at) VALUES (:org, :communityId, :mentor, :mentee, "
                + "'MENTEE', ARRAY['Kafka', 'Feedback']::varchar[], :message, 'OPEN', now())",
                Map.of("org", org, "communityId", communityId, "mentor", mentorId, "mentee", menteeId, "message",
                        message));
    }

    long pairing(long communityId, String mentorId, String menteeId) {

        var params = new HashMap<String, Object>();
        params.put("org", org);
        params.put("communityId", communityId);
        params.put("mentor", mentorId);
        params.put("mentee", menteeId);
        return jdbc.queryForObject("INSERT INTO mentoring_pairing (organization_id, community_id, mentor_user_id, "
                + "mentee_user_id, started_at) VALUES (:org, :communityId, :mentor, :mentee, now()) RETURNING id",
                params, Long.class);
    }

    long topic(long pairingId, String label) {

        return jdbc.queryForObject(
                "INSERT INTO mentoring_topic (organization_id, pairing_id, label, state, "
                        + "\"position\", created_at) VALUES (:org, :pairingId, :label, 'TODO', 1, now()) RETURNING id",
                Map.of("org", org, "pairingId", pairingId, "label", label), Long.class);
    }

    long note(long pairingId, String authorId, String body) {

        return jdbc.queryForObject(
                "INSERT INTO mentoring_note (organization_id, pairing_id, author_user_id, body, meeting_on, "
                        + "written_at) VALUES (:org, :pairingId, :author, :body, '2026-11-12', now()) RETURNING id",
                Map.of("org", org, "pairingId", pairingId, "author", authorId, "body", body), Long.class);
    }

    void noteTopic(long noteId, long topicId) {

        jdbc.update("INSERT INTO mentoring_note_topic (organization_id, note_id, topic_id) VALUES (:org, :noteId, "
                + ":topicId)", Map.of("org", org, "noteId", noteId, "topicId", topicId));
    }

    void meeting(long pairingId, String plannedBy, long topicId) {

        var meetingId = jdbc.queryForObject("INSERT INTO mentoring_meeting (organization_id, pairing_id, meeting_on, "
                + "planned_by, planned_at) VALUES (:org, :pairingId, '2026-11-26', :plannedBy, now()) RETURNING id",
                Map.of("org", org, "pairingId", pairingId, "plannedBy", plannedBy), Long.class);
        jdbc.update("INSERT INTO mentoring_meeting_topic (organization_id, meeting_id, topic_id) VALUES (:org, "
                + ":meetingId, :topicId)", Map.of("org", org, "meetingId", meetingId, "topicId", topicId));
    }

    void nextMeeting(long pairingId, String setBy) {

        jdbc.update(
                "UPDATE mentoring_pairing SET next_meeting_on = '2026-11-19', next_meeting_set_by = :setBy, "
                        + "next_meeting_set_at = now() WHERE organization_id = :org AND id = :pairingId",
                Map.of("org", org, "pairingId", pairingId, "setBy", setBy));
    }

    void linkOnTopic(long pairingId, long topicId, String url) {

        jdbc.update(
                "INSERT INTO mentoring_pointer (organization_id, pairing_id, topic_id, kind, url, created_at) "
                        + "VALUES (:org, :pairingId, :topicId, 'LINK', :url, now())",
                Map.of("org", org, "pairingId", pairingId, "topicId", topicId, "url", url));
    }

    void linkOnNote(long pairingId, long noteId, String url) {

        jdbc.update(
                "INSERT INTO mentoring_pointer (organization_id, pairing_id, note_id, kind, url, created_at) "
                        + "VALUES (:org, :pairingId, :noteId, 'LINK', :url, now())",
                Map.of("org", org, "pairingId", pairingId, "noteId", noteId, "url", url));
    }

    void memberOnTopic(long pairingId, long topicId, String userId) {

        jdbc.update(
                "INSERT INTO mentoring_pointer (organization_id, pairing_id, topic_id, kind, target_user_id, "
                        + "created_at) VALUES (:org, :pairingId, :topicId, 'MEMBER', :user, now())",
                Map.of("org", org, "pairingId", pairingId, "topicId", topicId, "user", userId));
    }

    void words(long pairingId, String body) {

        jdbc.update(
                "INSERT INTO mentoring_words (pairing_id, organization_id, body, written_at) "
                        + "VALUES (:pairingId, :org, :body, now())",
                Map.of("org", org, "pairingId", pairingId, "body", body));
    }

    void openWords(long pairingId) {

        jdbc.update(
                "UPDATE mentoring_words SET opened_at = now() WHERE organization_id = :org AND pairing_id = :pairingId",
                Map.of("org", org, "pairingId", pairingId));
    }

    void expiredNotice(String type, String subjectRef) {

        jdbc.update("INSERT INTO communication_expired_message (organization_id, message_type, subject_ref, "
                + "pending_count, sent_count, suppressed_count, unreachable_count, abandoned_count, expired_at) "
                + "VALUES (:org, :type, :subject, 0, 1, 0, 0, 0, now())",
                Map.of("org", org, "type", type, "subject", subjectRef));
    }

    long count(String table) {

        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE organization_id = :org",
                Map.of("org", org), Long.class);
    }
}
