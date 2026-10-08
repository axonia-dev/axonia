package dev.axonia.knowledge.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.recognition.ContributionSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactSourceAccessAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    private ArtifactRepository artifactRepository;

    @InjectMocks
    private ArtifactSourceAccessAdapter sut;

    @Test
    void artifactSource_returnsAuthoritativeAttribution() {

        // given
        var artifact = ArtifactEntityMother.any().id(5L).communityId(1L).contributorUserId("alice").title("RFC review")
                .build().toBuilder().createdAt(CREATED_AT).build();
        when(artifactRepository.findByOrganizationIdAndId("default", 5L)).thenReturn(Optional.of(artifact));

        // when
        var actual = sut.artifactSource("default", 5L);

        // then
        assertThat(actual).isPresent();
        assertThat(actual.get()).usingRecursiveComparison().isEqualTo(ContributionSource.builder().communityId(1L)
                .contributorUserIds(List.of("alice")).title("RFC review").occurredAt(CREATED_AT).build());
    }

    @Test
    void artifactSource_unknownArtifact_returnsEmpty() {

        // given
        when(artifactRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when
        var actual = sut.artifactSource("default", 99L);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void artifactsCapturedBy_returnsIdsOfArtifactsTheUserContributed() {

        // given
        var first = ArtifactEntityMother.any().id(5L).contributorUserId("alice").build();
        var second = ArtifactEntityMother.any().id(9L).contributorUserId("alice").build();
        when(artifactRepository.findByOrganizationIdAndContributorUserId("default", "alice"))
                .thenReturn(List.of(first, second));

        // when
        var actual = sut.artifactsCapturedBy("default", "alice");

        // then
        assertThat(actual).containsExactly(5L, 9L);
    }
}
