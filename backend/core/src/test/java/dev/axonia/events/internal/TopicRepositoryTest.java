package dev.axonia.events.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.TopicCounts;
import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicStatus;
import dev.axonia.events.mother.TopicEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

class TopicRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCommunities() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
    }

    @Test
    void save() {

        // given
        var topic = TopicEntityMother.any().build();

        // when
        var actual = topicRepository.save(topic);

        // then
        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getStatus()).isEqualTo(dev.axonia.events.TopicStatus.PROPOSED);
    }

    @Test
    void savePersistsTags() {

        // given
        var topic = TopicEntityMother.any().tags(java.util.Set.of(TopicTagRef.builder().tag("Kafka").build())).build();

        // when
        var saved = topicRepository.save(topic);
        var reloaded = topicRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();

        // then
        assertThat(reloaded.getTags()).extracting(TopicTagRef::getTag).containsExactlyInAnyOrder("Kafka");
    }

    @Test
    void findFiltered_filtersByKindAlone() {

        // given
        topicRepository.save(TopicEntityMother.any().kind(TopicKind.REQUEST).build());
        topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).build());

        // when
        var actual = topicRepository.findFiltered("default", 1L, new TopicFilter(TopicKind.OFFER, null),
                PageRequest.of(0, 20));

        // then
        assertThat(actual.getContent()).singleElement()
                .satisfies(e -> assertThat(e.getKind()).isEqualTo(TopicKind.OFFER));
    }

    @Test
    void findFiltered_filtersByKindAndStatus() {

        // given
        topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).status(TopicStatus.PROPOSED).build());
        topicRepository.save(TopicEntityMother.any().kind(TopicKind.OFFER).status(TopicStatus.DONE).build());

        // when
        var results = topicRepository.findFiltered("default", 1L,
                new TopicFilter(TopicKind.OFFER, TopicStatus.PROPOSED), PageRequest.of(0, 20));

        // then
        assertThat(results.getContent()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(TopicStatus.PROPOSED));
    }

    @Test
    void findFiltered_ordersByCreatedAtDescending_asTheControllerDefaultRequests() {

        // given three topics saved in an order that does not match their created_at, so the assertion cannot pass
        // by accident on insertion order
        var oldest = topicRepository.save(TopicEntityMother.any().title("Oldest").build()).getId();
        var newest = topicRepository.save(TopicEntityMother.any().title("Newest").build()).getId();
        var middle = topicRepository.save(TopicEntityMother.any().title("Middle").build()).getId();
        jdbc.update("UPDATE topic SET created_at = TIMESTAMPTZ '2020-01-01T00:00:00Z' WHERE id = ?", oldest);
        jdbc.update("UPDATE topic SET created_at = TIMESTAMPTZ '2020-06-01T00:00:00Z' WHERE id = ?", middle);
        jdbc.update("UPDATE topic SET created_at = TIMESTAMPTZ '2021-01-01T00:00:00Z' WHERE id = ?", newest);

        // when - the same sort property the controller's @PageableDefault requests
        var results = topicRepository.findFiltered("default", 1L, new TopicFilter(null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "created_at")));

        // then
        assertThat(results.getContent()).extracting(TopicEntity::getId).containsExactly(newest, middle, oldest);
    }

    @Test
    void findFiltered_breaksCreatedAtTiesByIdDescending() {

        // given two topics sharing the same created_at - a plain "created_at DESC" leaves their relative order
        // undefined, which is exactly the kind of gap that can repeat or skip a row across a page boundary
        var lowerId = topicRepository.save(TopicEntityMother.any().title("Lower id").build()).getId();
        var higherId = topicRepository.save(TopicEntityMother.any().title("Higher id").build()).getId();
        jdbc.update("UPDATE topic SET created_at = TIMESTAMPTZ '2020-01-01T00:00:00Z' WHERE id IN (?, ?)", lowerId,
                higherId);

        // when
        var results = topicRepository.findFiltered("default", 1L, new TopicFilter(null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "created_at")));

        // then - the hardcoded id-descending tiebreaker gives a stable order despite the tie
        assertThat(results.getContent()).extracting(TopicEntity::getId).containsExactly(higherId, lowerId);
    }

    @Test
    void findSchedules_resolvesSessionDateAndSeriesTitleForSlottedTopic() {
        // given a community + a PROPOSED topic
        var topicId = topicRepository.save(TopicEntityMother.any().communityId(1L).build()).getId();
        // a series, a future session, a section, and a slot carrying the topic
        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', 1, 'Frontend Dojo', 1, 'WEEK', DATE '2026-06-03', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                Long.class);
        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', ?, 1, 'SCHEDULED', TIMESTAMPTZ '2099-06-14T18:00:00Z', '2099-06-14', '', '', true, now(), now(), 0) RETURNING id",
                Long.class, seriesId);
        var sectionTypeId = jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, version) "
                        + "VALUES ('default', 'Talks', true, now(), now(), 0) RETURNING id",
                Long.class);
        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, position, section_type_id) VALUES (?, 0, ?) RETURNING id",
                Long.class, sessionId, sectionTypeId);
        var slotId = jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, topic_id, duration_minutes) VALUES (?, 0, ?, 20) RETURNING id",
                Long.class, sectionId, topicId);

        // when
        var rows = topicRepository.findSchedules("default", java.util.List.of(topicId));

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sessionId()).isEqualTo(sessionId);
        assertThat(rows.get(0).sectionId()).isEqualTo(sectionId);
        assertThat(rows.get(0).slotId()).isEqualTo(slotId);
        assertThat(rows.get(0).scheduleLabel()).isEqualTo("Frontend Dojo");
        assertThat(rows.get(0).scheduledAt()).isEqualTo(java.time.Instant.parse("2099-06-14T18:00:00Z"));
    }

    @Test
    void findSchedules_resolvesSessionTitleForTopicSlottedOnOneOffSession() {
        // given a topic slotted on a one-off session (no series) that carries its own title
        var topicId = topicRepository.save(TopicEntityMother.any().communityId(1L).build()).getId();
        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, title, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', NULL, 1, 'SCHEDULED', TIMESTAMPTZ '2099-06-14T18:00:00Z', 'Green IT audit review', '', '', true, now(), now(), 0) RETURNING id",
                Long.class);
        var sectionTypeId = jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, version) "
                        + "VALUES ('default', 'Talks', true, now(), now(), 0) RETURNING id",
                Long.class);
        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, position, section_type_id) VALUES (?, 0, ?) RETURNING id",
                Long.class, sessionId, sectionTypeId);
        var slotId = jdbc.queryForObject(
                "INSERT INTO event_slot (section_id, position, topic_id, duration_minutes) VALUES (?, 0, ?, 20) RETURNING id",
                Long.class, sectionId, topicId);

        // when
        var rows = topicRepository.findSchedules("default", java.util.List.of(topicId));

        // then - a one-off-scheduled topic still resolves, labelled by the session's own title
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sessionId()).isEqualTo(sessionId);
        assertThat(rows.get(0).slotId()).isEqualTo(slotId);
        assertThat(rows.get(0).scheduleLabel()).isEqualTo("Green IT audit review");
    }

    @Test
    void findSchedules_returnsEmptyForNoTopicIds() {

        assertThat(topicRepository.findSchedules("default", java.util.List.of())).isEmpty();
    }

    @Test
    void findSchedules_prefersUpcomingSessionOverPastOne() {
        // given a topic slotted on both a past and a future session
        var topicId = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(topicId, "2000-01-01T18:00:00Z", "SCHEDULED");
        var futureSessionId = slotTopicOnSessionAt(topicId, "2099-06-14T18:00:00Z", "SCHEDULED");

        // when
        var rows = topicRepository.findSchedules("default", java.util.List.of(topicId));

        // then - the future session's row comes first, so findFirst consumers pick the upcoming slot
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).sessionId()).isEqualTo(futureSessionId);
    }

    @Test
    void findSchedules_prefersSoonestOfSeveralUpcomingSessions() {
        // given a topic slotted on two future sessions
        var topicId = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(topicId, "2099-06-14T18:00:00Z", "SCHEDULED");
        var soonestSessionId = slotTopicOnSessionAt(topicId, "2098-01-01T18:00:00Z", "SCHEDULED");

        // when
        var rows = topicRepository.findSchedules("default", java.util.List.of(topicId));

        // then
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).sessionId()).isEqualTo(soonestSessionId);
    }

    @Test
    void findByOrganizationIdAndId_otherTenantIsIsolated() {

        // given
        var saved = topicRepository.save(TopicEntityMother.any().build());

        // when / then
        assertThat(topicRepository.findByOrganizationIdAndId("default", saved.getId())).isPresent();
        assertThat(topicRepository.findByOrganizationIdAndId("other-tenant", saved.getId())).isEmpty();
    }

    // --- findPast ---------------------------------------------------------

    private Long slotTopicOnSessionAt(Long topicId, String scheduledAtIso, String sessionStatus) {
        var seriesId = jdbc.queryForObject(
                "INSERT INTO event_series (organization_id, community_id, title, cadence_count, cadence_unit, starts_on, time_zone, status, created_at, updated_at, version) "
                        + "VALUES ('default', 1, 'Frontend Dojo', 1, 'WEEK', DATE '2026-06-03', 'UTC', 'ACTIVE', now(), now(), 0) RETURNING id",
                Long.class);
        var sessionId = jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, series_id, community_id, status, scheduled_at, occurrence_date, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES ('default', ?, 1, ?, CAST(? AS timestamptz), DATE '2026-06-03', '', '', true, now(), now(), 0) RETURNING id",
                Long.class, seriesId, sessionStatus, scheduledAtIso);
        var sectionTypeId = jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, created_at, updated_at, version) "
                        + "VALUES ('default', ?, true, now(), now(), 0) RETURNING id",
                Long.class, "Talks " + sessionId);
        var sectionId = jdbc.queryForObject(
                "INSERT INTO event_section (session_id, position, section_type_id) VALUES (?, 0, ?) RETURNING id",
                Long.class, sessionId, sectionTypeId);
        jdbc.update("INSERT INTO event_slot (section_id, position, topic_id, duration_minutes) VALUES (?, 0, ?, 20)",
                sectionId, topicId);
        return sessionId;
    }

    @Test
    void findPast_includesPastScheduledAndDone_excludesFutureAndProposed() {
        // given a past-scheduled topic, a future-scheduled topic, a done topic, and a proposed topic
        var pastScheduled = topicRepository
                .save(TopicEntityMother.any().title("Past talk").status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(pastScheduled, "2000-01-01T18:00:00Z", "SCHEDULED");

        var futureScheduled = topicRepository
                .save(TopicEntityMother.any().title("Future talk").status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(futureScheduled, "2099-06-14T18:00:00Z", "SCHEDULED");

        var done = topicRepository.save(TopicEntityMother.any().title("Done talk").status(TopicStatus.DONE).build())
                .getId();
        slotTopicOnSessionAt(done, "2001-01-01T18:00:00Z", "HELD");

        topicRepository.save(TopicEntityMother.any().title("Proposed talk").status(TopicStatus.PROPOSED).build());

        // when
        var page = topicRepository.findPast("default", 1L, null, PageRequest.of(0, 20));

        // then - past-scheduled + done only, newest session first (done @2001 before pastScheduled @2000)
        assertThat(page.getContent()).extracting(TopicEntity::getId).containsExactly(done, pastScheduled);
    }

    @Test
    void findPast_filtersByTitleSearch() {
        // given
        var matching = topicRepository
                .save(TopicEntityMother.any().title("Kafka retro").status(TopicStatus.DONE).build()).getId();
        slotTopicOnSessionAt(matching, "2001-01-01T18:00:00Z", "HELD");
        var other = topicRepository
                .save(TopicEntityMother.any().title("Grafana intro").status(TopicStatus.DONE).build()).getId();
        slotTopicOnSessionAt(other, "2001-01-01T18:00:00Z", "HELD");

        // when
        var page = topicRepository.findPast("default", 1L, "kafka", PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(TopicEntity::getId).containsExactly(matching);
    }

    @Test
    void findPast_searchMatchesDescription() {
        // given a done topic whose description (not title) contains the term
        var matching = topicRepository.save(TopicEntityMother.any().title("Retro session")
                .description("Lessons from our Kafka outage").status(TopicStatus.DONE).build()).getId();
        slotTopicOnSessionAt(matching, "2001-01-01T18:00:00Z", "HELD");
        var other = topicRepository.save(TopicEntityMother.any().title("Grafana intro").description("Dashboards 101")
                .status(TopicStatus.DONE).build()).getId();
        slotTopicOnSessionAt(other, "2001-01-01T18:00:00Z", "HELD");

        // when
        var page = topicRepository.findPast("default", 1L, "kafka", PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(TopicEntity::getId).containsExactly(matching);
    }

    @Test
    void findPast_paginates() {
        // given two past-done topics
        for (int i = 0; i < 2; i++) {
            var id = topicRepository.save(TopicEntityMother.any().title("Done " + i).status(TopicStatus.DONE).build())
                    .getId();
            slotTopicOnSessionAt(id, "200" + i + "-01-01T18:00:00Z", "HELD");
        }

        // when
        var first = topicRepository.findPast("default", 1L, null, PageRequest.of(0, 1));

        // then
        assertThat(first.getContent()).hasSize(1);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    // --- countByLifecycle -------------------------------------------------

    @Test
    void countByLifecycle_bucketsProposedUpcomingAndPast() {
        // given one proposed, one future-scheduled, one lapsed-scheduled, one done
        topicRepository.save(TopicEntityMother.any().status(TopicStatus.PROPOSED).build());
        var upcoming = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(upcoming, "2099-06-14T18:00:00Z", "SCHEDULED");
        var lapsed = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(lapsed, "2000-01-01T18:00:00Z", "SCHEDULED");
        var done = topicRepository.save(TopicEntityMother.any().status(TopicStatus.DONE).build()).getId();
        slotTopicOnSessionAt(done, "2001-01-01T18:00:00Z", "HELD");

        // when
        var counts = topicRepository.countByLifecycle("default", 1L);

        // then - the lapsed topic counts as done (it is what the Past tab lists), not scheduled
        assertThat(counts.proposed()).isEqualTo(1);
        assertThat(counts.scheduled()).isEqualTo(1);
        assertThat(counts.done()).isEqualTo(2);
    }

    @Test
    void countByLifecycle_ignoresCancelledSessionsAndOtherTenants() {
        // given a scheduled topic whose only session is cancelled
        var onCancelled = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build()).getId();
        slotTopicOnSessionAt(onCancelled, "2099-06-14T18:00:00Z", "CANCELLED");

        // when / then - neither scheduled nor done, and invisible to another tenant
        var counts = topicRepository.countByLifecycle("default", 1L);
        assertThat(counts.scheduled()).isZero();
        assertThat(counts.done()).isZero();
        assertThat(topicRepository.countByLifecycle("other-tenant", 1L))
                .isEqualTo(TopicCounts.builder().proposed(0).scheduled(0).done(0).build());
    }

    @Test
    void countByLifecycle_pastCancelledSessionCountsNeitherScheduledNorDone() {
        // given a scheduled topic whose only session is a past cancelled one
        var onPastCancelled = topicRepository.save(TopicEntityMother.any().status(TopicStatus.SCHEDULED).build())
                .getId();
        slotTopicOnSessionAt(onPastCancelled, "2000-01-01T18:00:00Z", "CANCELLED");

        // when
        var counts = topicRepository.countByLifecycle("default", 1L);

        // then - the done bucket's EXISTS excludes cancelled sessions too
        assertThat(counts.scheduled()).isZero();
        assertThat(counts.done()).isZero();
    }
}
