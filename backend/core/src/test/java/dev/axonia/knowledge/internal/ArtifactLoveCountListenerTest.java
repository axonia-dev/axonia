package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.recognition.ContributionLoved;
import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.ContributionUnloved;

import static org.assertj.core.api.Assertions.assertThat;

// Events are published inside a transaction so the listener's AFTER_COMMIT phase fires. Async is not enabled in this
// app, so @ApplicationModuleListener runs synchronously on commit — the count is settled by the time publishInTx
// returns, no polling needed.
@CoreSpringBootTest
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ArtifactLoveCountListenerTest {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void loved_incrementsLoveCountForCapturedArtifact() {

        // given
        var id = artifactRepository.save(ArtifactEntityMother.any().build()).getId();

        // when
        publishInTx(new ContributionLoved(ContributionType.ARTIFACT_CAPTURED, id, 1L));

        // then
        assertThat(loveCount(id)).isEqualTo(1);
    }

    @Test
    void unloved_decrementsLoveCountForCapturedArtifact() {

        // given
        var id = artifactRepository.save(ArtifactEntityMother.any().build()).getId();
        setLoveCount(id, 3);

        // when
        publishInTx(new ContributionUnloved(ContributionType.ARTIFACT_CAPTURED, id, 1L));

        // then
        assertThat(loveCount(id)).isEqualTo(2);
    }

    @Test
    void unloved_floorsLoveCountAtZero() {

        // given
        var id = artifactRepository.save(ArtifactEntityMother.any().build()).getId();

        // when
        publishInTx(new ContributionUnloved(ContributionType.ARTIFACT_CAPTURED, id, 1L));

        // then
        assertThat(loveCount(id)).isZero();
    }

    @Test
    void nonCapturedLove_leavesLoveCountUntouched() {

        // given
        var id = artifactRepository.save(ArtifactEntityMother.any().build()).getId();

        // when
        publishInTx(new ContributionLoved(ContributionType.SLOT_PRESENTED, id, 1L));

        // then
        assertThat(loveCount(id)).isZero();
    }

    @Test
    void nonCapturedUnlove_leavesLoveCountUntouched() {

        // given
        var id = artifactRepository.save(ArtifactEntityMother.any().build()).getId();
        setLoveCount(id, 2);

        // when
        publishInTx(new ContributionUnloved(ContributionType.SLOT_PRESENTED, id, 1L));

        // then
        assertThat(loveCount(id)).isEqualTo(2);
    }

    private void publishInTx(Object event) {

        new TransactionTemplate(txManager).executeWithoutResult(status -> events.publishEvent(event));
    }

    private void setLoveCount(Long id, int count) {

        jdbc.getJdbcTemplate().update("UPDATE artifact SET love_count = ? WHERE id = ?", count, id);
    }

    private int loveCount(Long id) {

        return jdbc.queryForObject("SELECT love_count FROM artifact WHERE id = :id",
                new MapSqlParameterSource("id", id), Integer.class);
    }
}
