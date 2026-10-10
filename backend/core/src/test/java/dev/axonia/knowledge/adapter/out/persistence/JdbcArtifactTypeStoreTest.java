package dev.axonia.knowledge.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.domain.StoredArtifactType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcArtifactTypeStoreTest {

    private static final Instant CREATED = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-03-02T10:00:00Z");

    @Mock
    private ArtifactTypeRepository artifactTypeRepository;

    private JdbcArtifactTypeStore sut() {

        return new JdbcArtifactTypeStore(artifactTypeRepository);
    }

    private static ArtifactTypeEntity fullEntity() {

        return ArtifactTypeEntityMother.any().id(4L).icon("presentation").accentColor("#d9871f").createdAt(CREATED)
                .updatedAt(UPDATED).version(1L).build();
    }

    @Test
    void find_mapsEveryColumn() {

        // given
        when(artifactTypeRepository.findByOrganizationIdAndId("default", 4L)).thenReturn(Optional.of(fullEntity()));

        // when
        var actual = sut().find("default", 4L);

        // then
        assertThat(actual).contains(StoredArtifactType.builder().id(4L).organizationId("default").label("Recording")
                .icon("presentation").accentColor("#d9871f").createdAt(CREATED).updatedAt(UPDATED).version(1L).build());
    }

    @Test
    void listByLabelAndFindAll_mapEachType() {

        // given
        when(artifactTypeRepository.findByOrganizationIdOrderByLabelAsc("default")).thenReturn(List.of(fullEntity()));
        when(artifactTypeRepository.findByOrganizationIdAndIdIn("default", Set.of(4L)))
                .thenReturn(List.of(fullEntity()));

        // when / then
        assertThat(sut().listByLabel("default")).extracting(StoredArtifactType::id).containsExactly(4L);
        assertThat(sut().findAll("default", Set.of(4L))).extracting(StoredArtifactType::label)
                .containsExactly("Recording");
    }

    @Test
    void labelExistsAndCounts_answerWhatTheRepositoryReads() {

        // given
        when(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "ADR")).thenReturn(true);
        when(artifactTypeRepository.countArtifactRefs(4L)).thenReturn(6L);
        when(artifactTypeRepository.countArtifactsByType("default"))
                .thenReturn(List.of(new ArtifactTypeRepository.TypeArtifactCount(4L, 6L),
                        new ArtifactTypeRepository.TypeArtifactCount(5L, 1L)));

        // when / then
        assertThat(sut().labelExists("default", "ADR")).isTrue();
        assertThat(sut().artifactCount(4L)).isEqualTo(6L);
        assertThat(sut().artifactCountsByType("default")).isEqualTo(Map.of(4L, 6L, 5L, 1L));
    }

    @Test
    void save_writesEveryColumnAndAnswersWhatWasStored() {

        // given
        var type = JdbcArtifactTypeStore.toType(fullEntity());
        when(artifactTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut().save(type);

        // then
        var written = ArgumentCaptor.forClass(ArtifactTypeEntity.class);
        verify(artifactTypeRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(fullEntity());
        assertThat(actual).isEqualTo(type);
    }

    @Test
    void reassignAndDelete_reachTheRepository() {

        // when
        sut().reassignArtifacts(4L, 5L);
        sut().delete(StoredArtifactType.builder().id(4L).version(2L).build());

        // then
        verify(artifactTypeRepository).reassignArtifactRefs(4L, 5L);
        var deleted = ArgumentCaptor.forClass(ArtifactTypeEntity.class);
        verify(artifactTypeRepository).delete(deleted.capture());
        assertThat(deleted.getValue().getId()).isEqualTo(4L);
        assertThat(deleted.getValue().getVersion()).isEqualTo(2L);
    }
}
