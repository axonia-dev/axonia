package dev.axonia.expertise.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.events.adapter.out.persistence.TopicEntityMother;
import dev.axonia.events.adapter.out.persistence.TopicRepository;
import dev.axonia.events.adapter.out.persistence.TopicTagRef;
import dev.axonia.events.api.TopicKind;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntityMother;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactTagRef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the backfill SQL in V11 correctly populates {@code expertise_signal}.
 *
 * <p>
 * Because Flyway runs V11 at container start (before tests seed data), this test reproduces the backfill logic as raw
 * SQL against data it seeds itself. This lets us confirm the normalisation expression and join paths produce the
 * expected rows.
 *
 * <p>
 * The connection runs as the Testcontainers superuser (table owner), which bypasses RLS - no {@code app.current_org}
 * setup is needed.
 */
class ExpertiseBackfillTest extends ExpertiseJdbcTestSupport {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seedCommunities() {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());
    }

    @Test
    void offeredTopicBackfill_producesOneSignalPerTag() {

        // given - OFFER topic with tag "Kafka"
        var topic = topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).proposerUserId("alice")
                .tags(java.util.Set.of(TopicTagRef.builder().tag("Kafka").build())).build());

        // when - run the OFFERED backfill logic
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT t.organization_id, t.community_id, t.proposer_user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(tt.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'OFFERED', 'TOPIC', t.id, t.created_at
                FROM topic t
                JOIN topic_tag tt ON tt.topic_id = t.id
                WHERE t.kind = 'OFFER'
                  AND t.id = :topicId
                  AND trim(both '-' from regexp_replace(
                        lower(translate(tt.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("topicId", topic.getId()));

        // then
        var signals = signalsForSource("TOPIC", topic.getId());
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0)).satisfies(row -> {
            assertThat(row.get("user_id")).isEqualTo("alice");
            assertThat(row.get("tag")).isEqualTo("kafka");
            assertThat(row.get("signal_type")).isEqualTo("OFFERED");
            assertThat(row.get("source_type")).isEqualTo("TOPIC");
        });
    }

    @Test
    void presentedSlotBackfill_producesOneSignalPerPresenterPerTag() {

        // given - OFFER topic with tag "spring-boot", a slot linked to it, and a presenter
        var topic = topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).proposerUserId("bob")
                .tags(java.util.Set.of(TopicTagRef.builder().tag("spring-boot").build())).build());

        // Seed the minimal session/section hierarchy and a slot linked to the topic.
        var slotId = seedSlotWithTopic(topic.getId());

        jdbc.update("INSERT INTO event_slot_presenter (slot_id, user_id) VALUES (:slotId, 'charlie')",
                new MapSqlParameterSource("slotId", slotId));

        final var finalSlotId = slotId;

        // when
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT t.organization_id, t.community_id, p.user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(tt.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'PRESENTED', 'SLOT', s.id, t.created_at
                FROM event_slot s
                JOIN event_slot_presenter p  ON p.slot_id  = s.id
                JOIN topic t                  ON t.id       = s.topic_id
                JOIN topic_tag tt             ON tt.topic_id = t.id
                WHERE s.id = :slotId
                  AND trim(both '-' from regexp_replace(
                        lower(translate(tt.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("slotId", finalSlotId));

        // then
        var signals = signalsForSource("SLOT", finalSlotId);
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0)).satisfies(row -> {
            assertThat(row.get("user_id")).isEqualTo("charlie");
            assertThat(row.get("tag")).isEqualTo("spring-boot");
            assertThat(row.get("signal_type")).isEqualTo("PRESENTED");
        });
    }

    @Test
    void contributedArtifactBackfill_producesOneSignalPerTag() {

        // given - active artifact with tag "resilience"
        var artifact = artifactRepository.save(ArtifactEntityMother.any().contributorUserId("diana").status("ACTIVE")
                .tags(java.util.Set.of(ArtifactTagRef.builder().tag("resilience").build())).build());

        // when
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT a.organization_id, a.community_id, a.contributor_user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(at.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'CONTRIBUTED', 'ARTIFACT', a.id, a.created_at
                FROM artifact a
                JOIN artifact_tag at ON at.artifact_id = a.id
                WHERE a.id = :artifactId
                  AND a.status = 'ACTIVE'
                  AND trim(both '-' from regexp_replace(
                        lower(translate(at.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("artifactId", artifact.getId()));

        // then
        var signals = signalsForSource("ARTIFACT", artifact.getId());
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0)).satisfies(row -> {
            assertThat(row.get("user_id")).isEqualTo("diana");
            assertThat(row.get("tag")).isEqualTo("resilience");
            assertThat(row.get("signal_type")).isEqualTo("CONTRIBUTED");
        });
    }

    @Test
    void normalisationLowercasesAndStripsHyphens() {

        // given - tag with uppercase and spaces
        var topic = topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).proposerUserId("eve")
                .tags(java.util.Set.of(TopicTagRef.builder().tag("Apache Kafka").build())).build());

        // when
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT t.organization_id, t.community_id, t.proposer_user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(tt.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'OFFERED', 'TOPIC', t.id, t.created_at
                FROM topic t
                JOIN topic_tag tt ON tt.topic_id = t.id
                WHERE t.id = :topicId
                  AND trim(both '-' from regexp_replace(
                        lower(translate(tt.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("topicId", topic.getId()));

        // then - "Apache Kafka" normalises to "apache-kafka"
        var signals = signalsForSource("TOPIC", topic.getId());
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).get("tag")).isEqualTo("apache-kafka");
    }

    @Test
    void archivedArtifactIsNotBackfilled() {

        // given
        var artifact = artifactRepository.save(ArtifactEntityMother.any().contributorUserId("frank").status("ARCHIVED")
                .tags(java.util.Set.of(ArtifactTagRef.builder().tag("java").build())).build());

        // when - run contributed backfill (includes WHERE status = 'ACTIVE')
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT a.organization_id, a.community_id, a.contributor_user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(at.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'CONTRIBUTED', 'ARTIFACT', a.id, a.created_at
                FROM artifact a
                JOIN artifact_tag at ON at.artifact_id = a.id
                WHERE a.id = :artifactId
                  AND a.status = 'ACTIVE'
                  AND trim(both '-' from regexp_replace(
                        lower(translate(at.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("artifactId", artifact.getId()));

        // then - archived artifact produces no signal
        assertThat(signalsForSource("ARTIFACT", artifact.getId())).isEmpty();
    }

    @Test
    void requestTopicIsNotBackfilled() {

        // given - REQUEST topic (not OFFER)
        var topic = topicRepository.save(TopicEntityMother.any().kind(TopicKind.REQUEST)
                .tags(java.util.Set.of(TopicTagRef.builder().tag("go").build())).build());

        // when - run OFFERED backfill (WHERE kind='OFFER')
        jdbc.update("""
                INSERT INTO expertise_signal
                    (organization_id, community_id, user_id, tag, signal_type, source_type, source_id, occurred_at)
                SELECT t.organization_id, t.community_id, t.proposer_user_id,
                       trim(both '-' from regexp_replace(
                         lower(translate(tt.tag,
                           'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                           'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                         '[^a-z0-9]+', '-', 'g')),
                       'OFFERED', 'TOPIC', t.id, t.created_at
                FROM topic t
                JOIN topic_tag tt ON tt.topic_id = t.id
                WHERE t.kind = 'OFFER'
                  AND t.id = :topicId
                  AND trim(both '-' from regexp_replace(
                        lower(translate(tt.tag,
                          'àáâãäåèéêëìíîïòóôõöùúûüçñÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖÙÚÛÜÇÑ',
                          'aaaaaaeeeeiiiiooooouuuucnaaaaaaeeeeiiiiooooouuuucn')),
                        '[^a-z0-9]+', '-', 'g')) <> ''
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("topicId", topic.getId()));

        // then - REQUEST topic produces no OFFERED signal
        assertThat(signalsForSource("TOPIC", topic.getId())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private List<java.util.Map<String, Object>> signalsForSource(String sourceType, Long sourceId) {

        return jdbc.queryForList(
                "SELECT * FROM expertise_signal WHERE source_type = :sourceType AND source_id = :sourceId",
                new MapSqlParameterSource("sourceType", sourceType).addValue("sourceId", sourceId));
    }

    /**
     * Seeds the minimal session/section hierarchy so that an event_slot row can be inserted with the given topicId.
     * Returns the new slot id.
     */
    private Long seedSlotWithTopic(Long topicId) {

        jdbc.update(
                "INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                        + "VALUES ('default', 'BackfillKind', now(), now(), 0) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource());

        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, kind_id, description, status, created_at, updated_at, version) "
                        + "SELECT 'default', 'backfill-' || nextval('communities_id_seq'), 'BF', k.id, '', 'ACTIVE', now(), now(), 0 "
                        + "FROM community_kind k WHERE k.organization_id='default' AND k.label='BackfillKind' RETURNING id",
                new MapSqlParameterSource(), Long.class);

        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, preferred_time, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', :communityId, 'BF Series', 1, 'WEEK', '2026-01-01', '18:00', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("communityId", communityId), Long.class);

        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', :seriesId, :communityId, 'SCHEDULED', now(), now()::date, '', '', true, now(), now(), 0) RETURNING id",
                new MapSqlParameterSource("seriesId", seriesId).addValue("communityId", communityId), Long.class);

        var sectionTypeId = jdbc.queryForObject("INSERT INTO event_section_type "
                + "(organization_id, label, expects_presenter, created_at, updated_at, version, icon, accent_color) "
                + "VALUES ('default', 'BackfillSection', true, now(), now(), 0, 'wrench', '#123456') RETURNING id",
                new MapSqlParameterSource(), Long.class);

        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, section_type_id, title, position, duration_minutes) "
                        + "VALUES (:sessionId, :sectionTypeId, 'Talks', 0, 30) RETURNING id",
                new MapSqlParameterSource("sessionId", sessionId).addValue("sectionTypeId", sectionTypeId), Long.class);

        return jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, topic_id, duration_minutes) "
                        + "VALUES (:sectionId, 1, :topicId, 20) RETURNING id",
                new MapSqlParameterSource("sectionId", sectionId).addValue("topicId", topicId), Long.class);
    }
}
