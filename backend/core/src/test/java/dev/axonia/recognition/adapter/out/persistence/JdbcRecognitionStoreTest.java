package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.domain.Recognition;
import dev.axonia.recognition.domain.RecognitionMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcRecognitionStoreTest {

    private static final Recognition STORED = RecognitionMother.any().id(5L)
            .createdAt(Instant.parse("2026-06-02T00:00:00Z")).updatedAt(Instant.parse("2026-06-03T00:00:00Z"))
            .version(2L).build();

    private static final Recognition CANDIDATE = RecognitionMother.any().title("Talk").build();

    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");

    @Mock
    private RecognizedContributionRepository recognizedContributionRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private Clock clock;

    @InjectMocks
    private JdbcRecognitionStore sut;

    @Test
    void findMapsEveryColumn() {

        // given
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(JdbcRecognitionStore.toEntity(STORED)));

        // when
        var actual = sut.find("default", ContributionType.SLOT_PRESENTED, 100L);

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void findAllKeepsTheRepositoryOrder() {

        // given
        var other = RecognitionMother.any().id(6L).contributionId(101L).build();
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("default",
                ContributionType.SLOT_PRESENTED, List.of(100L, 101L)))
                .thenReturn(List.of(JdbcRecognitionStore.toEntity(STORED), JdbcRecognitionStore.toEntity(other)));

        // when
        var actual = sut.findAll("default", ContributionType.SLOT_PRESENTED, List.of(100L, 101L));

        // then
        assertThat(actual).containsExactly(STORED, other);
    }

    @Test
    void recognizeReusesAnExistingRecognition() {

        // given
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.of(JdbcRecognitionStore.toEntity(STORED)));

        // when
        var actual = sut.recognize(CANDIDATE);

        // then
        assertThat(actual).isEqualTo(STORED);
        verifyNoInteractions(jdbc);
    }

    @Test
    void recognizeInsertsTheCandidateWhenNoneIsHeldAndAnswersTheStoredRow() {

        // given
        when(clock.instant()).thenReturn(NOW);
        when(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("default",
                ContributionType.SLOT_PRESENTED, 100L)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(JdbcRecognitionStore.toEntity(STORED)));

        // when
        var actual = sut.recognize(CANDIDATE);

        // then
        assertThat(actual).isEqualTo(STORED);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("ON CONFLICT (organization_id, contribution_type, contribution_id)")
                .contains("DO NOTHING");
        assertThat(params.getValue().getValues()).containsEntry("org", CANDIDATE.organizationId())
                .containsEntry("contributionType", CANDIDATE.contributionType().name())
                .containsEntry("contributionId", CANDIDATE.contributionId())
                .containsEntry("communityId", CANDIDATE.communityId()).containsEntry("title", CANDIDATE.title())
                .containsEntry("occurredAt", Timestamp.from(CANDIDATE.occurredAt()))
                .containsEntry("now", Timestamp.from(NOW));
    }
}
