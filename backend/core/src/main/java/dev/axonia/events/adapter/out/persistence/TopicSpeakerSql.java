package dev.axonia.events.adapter.out.persistence;

/**
 * The one definition of who presents topic {@code t}: its {@code topic_speaker} rows while it is on the backlog, and
 * its slot's presenters on sessions that are not cancelled once it has left it. Every query on a topic's speakers or
 * kind goes through here, so the two can never disagree; a backlog topic already loaded reads the same rows from its
 * own collection.
 */
final class TopicSpeakerSql {

    /** One {@code user_id} column per speaker of {@code t}, distinct. */
    static final String SPEAKERS_OF_T = """
            SELECT ts.user_id FROM topic_speaker ts
            WHERE ts.topic_id = t.id AND t.status = 'PROPOSED'
            UNION
            SELECT esp.user_id FROM event_slot sl
            JOIN event_section sec ON sl.section_id = sec.id
            JOIN community_session cs ON sec.session_id = cs.id
            JOIN event_slot_presenter esp ON esp.slot_id = sl.id
            WHERE sl.topic_id = t.id AND t.status <> 'PROPOSED' AND cs.status <> 'CANCELLED'
            """;

    static final String HAS_SPEAKER = "EXISTS (" + SPEAKERS_OF_T + ")";

    private TopicSpeakerSql() {
    }
}
