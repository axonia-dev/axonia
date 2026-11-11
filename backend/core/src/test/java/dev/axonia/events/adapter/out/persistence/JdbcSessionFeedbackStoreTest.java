package dev.axonia.events.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.domain.FeedbackRecord;
import dev.axonia.events.domain.FeedbackTally;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSessionFeedbackStoreTest extends CoreJdbcTestSupport {

    @Autowired
    private SessionFeedbackRepository sessionFeedbackRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private JdbcSessionFeedbackStore sut;

    private long sessionId;

    @BeforeEach
    void seed() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        var seriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        sessionId = sessionRepository.save(SessionEntityMother.any().seriesId(seriesId).communityId(1L).build())
                .getId();
        sut = new JdbcSessionFeedbackStore(sessionFeedbackRepository);
    }

    @Test
    void save_thenFindMine() {

        // given
        var saved = sut.save(feedback("alice", 9, 7));

        // when
        var mine = sut.findMine("default", sessionId, "alice");

        // then
        assertThat(mine).get().usingRecursiveComparison().isEqualTo(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(sut.findMine("default", sessionId, "bob")).isEmpty();
        assertThat(sut.findMine("other", sessionId, "alice")).isEmpty();
    }

    @Test
    void save_replacesTheAnswerOfAnExistingRow() {

        // given
        var first = sut.save(feedback("alice", 9, 7));

        // when
        sut.save(first.toBuilder().enjoyed(4).learned(5).build());

        // then
        assertThat(sut.findMine("default", sessionId, "alice")).get()
                .extracting(FeedbackRecord::getEnjoyed, FeedbackRecord::getLearned, FeedbackRecord::getVersion)
                .containsExactly(4, 5, 1L);
        assertThat(sut.tally("default", sessionId).answers()).isEqualTo(1);
    }

    @Test
    void save_secondRowForSameMemberViolatesTheUniqueConstraint() {

        // given
        sut.save(feedback("alice", 9, 7));

        // when / then
        assertThatThrownBy(() -> sut.save(feedback("alice", 3, 3))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tally_countsAnswersAndSums() {

        // given
        sut.save(feedback("alice", 9, 7));
        sut.save(feedback("bob", 8, 6));
        sut.save(feedback("carol", 8, 10));

        // when
        var tally = sut.tally("default", sessionId);

        // then
        assertThat(tally).isEqualTo(new FeedbackTally(3, 3, 25.0, 3, 23.0));
        assertThat(tally.enjoyedAverage()).isEqualTo(8.3);
        assertThat(sut.anyForSession("default", sessionId)).isTrue();
    }

    @Test
    void tally_isEmptyForASessionWithoutAnswers() {

        // when
        var tally = sut.tally("default", sessionId);

        // then
        assertThat(tally).isEqualTo(new FeedbackTally(0, 0, 0.0, 0, 0.0));
        assertThat(sut.anyForSession("default", sessionId)).isFalse();
    }

    private FeedbackRecord feedback(String userId, int enjoyed, int learned) {

        return FeedbackRecord.builder().organizationId("default").sessionId(sessionId).userId(userId).enjoyed(enjoyed)
                .learned(learned).build();
    }
}
