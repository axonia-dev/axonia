package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.FeedbackTally;
import dev.axonia.events.domain.ImportedAnswer;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFeedbackImportStoreTest extends CoreJdbcTestSupport {

    private static final Instant IMPORTED_AT = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private JdbcFeedbackImportStore sut;

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
        sut = new JdbcFeedbackImportStore(namedJdbc);
    }

    @Test
    void replace_storesAnswersAndTalliesOnTheTenPointRange() {

        // given
        var answers = List.of(new ImportedAnswer(5, 3), new ImportedAnswer(4, null));

        // when
        sut.replace(feedbackImport(5, "Microsoft Forms", answers));

        // then
        assertThat(sut.tally("default", sessionId)).isEqualTo(new FeedbackTally(2, 2, 10.0 + 7.75, 1, 5.5));
        assertThat(sut.find("default", sessionId)).get().satisfies(stored -> {
            assertThat(stored.getId()).isNotNull();
            assertThat(stored.getOrganizationId()).isEqualTo("default");
            assertThat(stored.getSessionId()).isEqualTo(sessionId);
            assertThat(stored.getScale()).isEqualTo(5);
            assertThat(stored.getSource()).isEqualTo("Microsoft Forms");
            assertThat(stored.getCreatedAt()).isEqualTo(IMPORTED_AT);
            assertThat(stored.getAnswers()).containsExactlyElementsOf(answers);
        });
    }

    @Test
    void replace_withNoAnswersRemovesTheImport() {

        // given
        sut.replace(feedbackImport(10, "Microsoft Forms", List.of(new ImportedAnswer(8, 9))));

        // when
        sut.replace(feedbackImport(10, "Microsoft Forms", List.of()));

        // then
        assertThat(sut.find("default", sessionId)).isEmpty();
        assertThat(sut.tally("default", sessionId)).isEqualTo(new FeedbackTally(0, 0, 0.0, 0, 0.0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_feedback_import_answer", Long.class)).isZero();
    }

    @Test
    void replace_twiceKeepsOnlyTheSecond() {

        // given
        sut.replace(feedbackImport(5, "Google Forms", List.of(new ImportedAnswer(1, 1), new ImportedAnswer(2, 2))));

        // when
        sut.replace(feedbackImport(10, "Microsoft Forms", List.of(new ImportedAnswer(null, 7))));

        // then
        assertThat(sut.find("default", sessionId)).get().satisfies(stored -> {
            assertThat(stored.getScale()).isEqualTo(10);
            assertThat(stored.getSource()).isEqualTo("Microsoft Forms");
            assertThat(stored.getAnswers()).containsExactly(new ImportedAnswer(null, 7));
        });
        assertThat(sut.tally("default", sessionId)).isEqualTo(new FeedbackTally(1, 0, 0.0, 1, 7.0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_feedback_import_answer", Long.class))
                .isEqualTo(1L);
    }

    private FeedbackImportRecord feedbackImport(int scale, String source, List<ImportedAnswer> answers) {

        return FeedbackImportRecord.builder().organizationId("default").sessionId(sessionId).scale(scale).source(source)
                .answers(answers).createdAt(IMPORTED_AT).build();
    }
}
