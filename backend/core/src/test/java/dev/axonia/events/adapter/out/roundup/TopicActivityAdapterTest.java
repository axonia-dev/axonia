package dev.axonia.events.adapter.out.roundup;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;

import static org.assertj.core.api.Assertions.assertThat;

class TopicActivityAdapterTest extends CoreJdbcTestSupport {

    /**
     * Its own organization, never the shared "default": this port answers for a whole organization at once, so a row
     * another test committed there would be part of the answer.
     */
    private static final String ORG = "roundup-topics";
    private static final String OTHER_ORG = "roundup-topics-other";
    private static final Instant SCHEDULED_AT = Instant.parse("2026-05-12T17:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private TopicActivityAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new TopicActivityAdapter(jdbc);
        seedReferenceCatalogs();
    }

    @Test
    void tagsAreCountedAcrossTheQuartersSessions_busiestFirst() {

        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, ORG);
        var sectionId = section(sessionId);
        tag(slot(sectionId, "Kafka"), "kafka");
        tag(slot(sectionId, "Streams"), "kafka");
        tag(slot(sectionId, "Spring @AutoMvc"), "spring");

        assertThat(sut.tagsForSessions(ORG, List.of(sessionId))).containsExactly(new TagCount("kafka", 2),
                new TagCount("spring", 1));
    }

    @Test
    void aQuarterThatHeldNothingAsksTheDatabaseNothing() {

        assertThat(sut.tagsForSessions(ORG, List.of())).isEmpty();
    }

    /**
     * "kafka" also sorts before "spring" alphabetically, so the test above would still pass with count order dropped
     * entirely. This picks tags where the alphabetical and busiest-first orderings disagree, so a query that lost its
     * {@code count(*) DESC} and fell back to {@code est.tag} alone would list "alpha" first and fail here.
     */
    @Test
    void aBusierTagOutranksAnAlphabeticallyEarlierButRarerOne() {

        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, ORG);
        var sectionId = section(sessionId);
        tag(slot(sectionId, "Zebra talk one"), "zebra");
        tag(slot(sectionId, "Zebra talk two"), "zebra");
        tag(slot(sectionId, "Alpha talk"), "alpha");

        assertThat(sut.tagsForSessions(ORG, List.of(sessionId))).containsExactly(new TagCount("zebra", 2),
                new TagCount("alpha", 1));
    }

    /**
     * The tests above never tie, so neither proves the tie-break: two tags used once each must still come back
     * alphabetically, not in insertion order or some other incidental order.
     */
    @Test
    void tiedCountsBreakAlphabeticallyByTag() {

        var guild = community("backend-guild", ORG);
        var sessionId = session(guild, ORG);
        var sectionId = section(sessionId);
        tag(slot(sectionId, "Zookeeper internals"), "zookeeper");
        tag(slot(sectionId, "Actor model"), "actors");

        assertThat(sut.tagsForSessions(ORG, List.of(sessionId))).containsExactly(new TagCount("actors", 1),
                new TagCount("zookeeper", 1));
    }

    @Test
    void anotherOrganizationsTaggedTalksAreNotCounted() {

        // given a tagged slot on a session that belongs to another organization entirely
        var theirs = community("backend-guild", OTHER_ORG);
        var theirSessionId = session(theirs, OTHER_ORG);
        tag(slot(section(theirSessionId), "Kafka"), "kafka");

        // when / then: asking as ORG for that same session id must not see the other tenant's tag
        assertThat(sut.tagsForSessions(ORG, List.of(theirSessionId))).isEmpty();
    }

    /** The baseline seeds no reference catalogs, so the rows this test's inserts select must be its own. */
    private void seedReferenceCatalogs() {

        seedReferenceCatalogs(ORG);
        seedReferenceCatalogs(OTHER_ORG);
    }

    private void seedReferenceCatalogs(String organizationId) {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES (:org, 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", organizationId));
        jdbc.update("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES (:org, 'Talks', true, now(), now(), 0, 'wrench', '#123456') ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("org", organizationId));
    }

    // ----- raw-SQL seeding: created_at is auditing-managed, so repositories can't backdate rows -----

    private long community(String slug, String organizationId) {

        return jdbc.queryForObject("""
                INSERT INTO communities (organization_id, slug, name, kind_id, description, status,
                        can_start_series, can_edit_structure, can_queue_topics, created_at, updated_at, version)
                VALUES (:org, :slug, 'Guild',
                        (SELECT min(id) FROM community_kind WHERE organization_id = :org), 'd', 'ACTIVE',
                        false, false, false, now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource("slug", slug).addValue("org", organizationId), Long.class);
    }

    private long session(long communityId, String organizationId) {

        return jdbc.queryForObject("""
                INSERT INTO community_session (organization_id, community_id, status, scheduled_at,
                        title, notes, recording_url, has_time, created_at, updated_at, version)
                VALUES (:org, :communityId, 'HELD', :scheduledAt, null, '', '', true, now(), now(), 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("communityId", communityId)
                        .addValue("scheduledAt", Timestamp.from(SCHEDULED_AT)).addValue("org", organizationId),
                Long.class);
    }

    private long section(long sessionId) {

        return jdbc.queryForObject("""
                INSERT INTO event_section (session_id, position, section_type_id, title)
                VALUES (:sessionId, (SELECT count(*) FROM event_section WHERE session_id = :sessionId),
                        (SELECT min(t.id) FROM event_section_type t
                         JOIN community_session s ON s.organization_id = t.organization_id
                         WHERE s.id = :sessionId), 'Talks')
                RETURNING id
                """, new MapSqlParameterSource("sessionId", sessionId), Long.class);
    }

    private long slot(long sectionId, String title) {

        return jdbc.queryForObject("""
                INSERT INTO event_slot (section_id, position, title, duration_minutes)
                VALUES (:sectionId, (SELECT count(*) FROM event_slot WHERE section_id = :sectionId), :title, 15)
                RETURNING id
                """, new MapSqlParameterSource("sectionId", sectionId).addValue("title", title), Long.class);
    }

    private void tag(long slotId, String tag) {

        jdbc.update("INSERT INTO event_slot_tag (slot_id, tag) VALUES (:slotId, :tag)",
                new MapSqlParameterSource().addValue("slotId", slotId).addValue("tag", tag));
    }
}
