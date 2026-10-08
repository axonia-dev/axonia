package dev.axonia.events.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.mother.AttendanceEntityMother;
import dev.axonia.events.mother.SeriesEntityMother;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AttendanceRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long sessionId;

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
    }

    @Test
    void countByOrganizationIdAndSessionId() {

        // given
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(sessionId).userId("alice").build());
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(sessionId).userId("bob").build());

        // when / then
        assertThat(attendanceRepository.countByOrganizationIdAndSessionId("default", sessionId)).isEqualTo(2L);
    }

    @Test
    void countBySessionIdIn() {

        // given - a second session with its own attendees, to prove the count is grouped per session
        var otherSeriesId = seriesRepository.save(SeriesEntityMother.any().communityId(1L).build()).getId();
        var otherSessionId = sessionRepository
                .save(SessionEntityMother.any().seriesId(otherSeriesId).communityId(1L).build()).getId();
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(sessionId).userId("alice").build());
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(sessionId).userId("bob").build());
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(otherSessionId).userId("carol").build());

        // when
        var actual = attendanceRepository.countBySessionIdIn("default", List.of(sessionId, otherSessionId));

        // then
        assertThat(actual).extracting(SessionAttendeeCount::sessionId, SessionAttendeeCount::attendeeCount)
                .containsExactlyInAnyOrder(tuple(sessionId, 2L), tuple(otherSessionId, 1L));
    }

    /**
     * Unlike a derived {@code findBy...In} query, this hand-written {@code @Query} does not short-circuit an empty
     * collection on its own: Postgres rejects the resulting {@code IN ()} outright. This is exactly why
     * {@code SessionMapper.attendeeCountsOf} guards the empty case itself before ever calling this method - pinned here
     * so that guard is never mistaken for redundant.
     */
    @Test
    void countBySessionIdIn_emptyIdsIsARealPostgresSyntaxErrorNotAnEmptyResult() {

        assertThatThrownBy(() -> attendanceRepository.countBySessionIdIn("default", List.of()))
                .isInstanceOf(BadSqlGrammarException.class);
    }

    @Test
    void save_duplicateAttendance_violatesUniqueConstraint() {

        // given
        attendanceRepository.save(AttendanceEntityMother.any().sessionId(sessionId).userId("alice").build());

        // when / then
        assertThatThrownBy(() -> attendanceRepository
                .save(AttendanceEntityMother.any().sessionId(sessionId).userId("alice").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
