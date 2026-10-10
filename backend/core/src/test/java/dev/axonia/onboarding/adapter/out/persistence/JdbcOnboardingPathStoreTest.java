package dev.axonia.onboarding.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.onboarding.domain.OnboardingPathRecord;
import dev.axonia.onboarding.domain.OnboardingStepRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcOnboardingPathStoreTest {

    private static final OnboardingPathRecord STORED = OnboardingPathRecord.builder().subject("alice").variant("MEMBER")
            .communitySlug("guild").anchorOrganizationId("org-1").anchorCommunityId(7L)
            .closedAt(Instant.parse("2026-06-03T00:00:00Z"))
            .steps(List.of(
                    OnboardingStepRecord.builder().stepKey("LIBRARY").route("/communities/guild/library")
                            .detail("Clean Code").visitedAt(Instant.parse("2026-06-02T00:00:00Z")).build(),
                    OnboardingStepRecord.builder().stepKey("HEALTH").route("/communities/guild/health").build()))
            .createdAt(Instant.parse("2026-06-01T00:00:00Z")).updatedAt(Instant.parse("2026-06-03T00:00:00Z")).build();

    @Mock
    private OnboardingPathRepository repository;

    @InjectMocks
    private JdbcOnboardingPathStore sut;

    @Test
    void find_maps_every_column_and_every_step() {

        // given
        when(repository.findById("alice")).thenReturn(Optional.of(JdbcOnboardingPathStore.toEntity(STORED, false)));

        // then
        assertThat(sut.find("alice")).contains(STORED);
    }

    @Test
    void add_writes_a_new_path() {

        // given
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.add(STORED);

        // then
        var captor = ArgumentCaptor.forClass(OnboardingPathEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isNew()).isTrue();
        assertThat(JdbcOnboardingPathStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void update_writes_over_the_existing_path() {

        // given
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.update(STORED);

        // then
        var captor = ArgumentCaptor.forClass(OnboardingPathEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isNew()).isFalse();
        assertThat(JdbcOnboardingPathStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }
}
