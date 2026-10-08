package dev.axonia.knowledge.internal;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.ArtifactStatus;
import dev.axonia.knowledge.mother.ArtifactEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactMapperTest {

    @Mock
    private ArtifactTypeRepository artifactTypeRepository;

    @InjectMocks
    private ArtifactMapper sut;

    @Test
    void mapsTypeLabelTagsAndStatus() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        var entity = ArtifactEntityMother.any().id(10L).loveCount(7)
                .tags(Set.of(ArtifactTagRef.builder().tag("b").build(), ArtifactTagRef.builder().tag("a").build()))
                .build();

        // when
        var artifact = sut.toArtifact(entity);

        // then
        assertThat(artifact.type()).isEqualTo("Recording");
        assertThat(artifact.status()).isEqualTo(ArtifactStatus.ACTIVE);
        assertThat(artifact.tags()).containsExactly("a", "b");
        assertThat(artifact.loveCount()).isEqualTo(7);
        assertThat(artifact.provenance()).isNull();
    }

    @Test
    void buildsProvenanceWhenSessionPresent() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndIdIn("default", Set.of(1L)))
                .thenReturn(List.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        var entity = ArtifactEntityMother.any().provenanceSessionId(55L).provenanceSegmentId(2L)
                .provenanceLabel("Frontend Dojo").build();

        // when
        var provenance = sut.toArtifact(entity).provenance();

        // then
        assertThat(provenance.sessionId()).isEqualTo(55L);
        assertThat(provenance.segmentId()).isEqualTo(2L);
        assertThat(provenance.label()).isEqualTo("Frontend Dojo");
    }

    @Test
    void toleratesNullTagsAndUnknownType() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndIdIn("default", Set.of(1L))).thenReturn(List.of());
        var entity = ArtifactEntityMother.any().tags(null).build();

        // when
        var artifact = sut.toArtifact(entity);

        // then
        assertThat(artifact.type()).isNull();
        assertThat(artifact.tags()).isEmpty();
        assertThat(artifact.loveCount()).isZero();
    }

    @Test
    void toArtifacts_resolvesEveryDistinctTypeInOneBatchAndToleratesANullTypeId() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndIdIn("default", Set.of(1L, 2L)))
                .thenReturn(List.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build(),
                        ArtifactTypeEntity.builder().id(2L).label("Slides").build()));
        var typed1 = ArtifactEntityMother.any().id(1L).typeId(1L).tags(Set.of()).build();
        var typed2 = ArtifactEntityMother.any().id(2L).typeId(2L).tags(Set.of()).build();
        var untyped = ArtifactEntityMother.any().id(3L).typeId(null).tags(Set.of()).build();

        // when
        var artifacts = sut.toArtifacts(List.of(typed1, typed2, untyped));

        // then
        assertThat(artifacts).extracting(a -> a.type()).containsExactly("Recording", "Slides", null);
    }

    @Test
    void toArtifacts_emptyBatchResolvesNoTypesAndQueriesNothing() {

        // when
        var artifacts = sut.toArtifacts(List.of());

        // then
        assertThat(artifacts).isEmpty();
        verifyNoInteractions(artifactTypeRepository);
    }
}
