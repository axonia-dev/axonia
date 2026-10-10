package dev.axonia.knowledge.adapter.out.persistence;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import dev.axonia.knowledge.domain.StoredArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcArtifactStoreTest {

    private static final Instant CREATED = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-03-02T10:00:00Z");

    @Mock
    private ArtifactRepository artifactRepository;

    private JdbcArtifactStore sut() {

        return new JdbcArtifactStore(artifactRepository);
    }

    private static ArtifactEntity fullEntity() {

        var tags = new LinkedHashSet<ArtifactTagRef>();
        tags.add(ArtifactTagRef.builder().tag("kafka").build());
        tags.add(ArtifactTagRef.builder().tag("ddd").build());
        return ArtifactEntityMother.any().id(7L).provenanceSessionId(42L).provenanceSegmentId(3L)
                .provenanceLabel("Talk").loveCount(5).tags(tags).createdAt(CREATED).updatedAt(UPDATED).version(2L)
                .build();
    }

    @Test
    void find_mapsEveryColumnAndKeepsTagOrder() {

        // given
        when(artifactRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(fullEntity()));

        // when
        var actual = sut().find("default", 7L);

        // then
        assertThat(actual).get().satisfies(artifact -> {
            assertThat(artifact.id()).isEqualTo(7L);
            assertThat(artifact.organizationId()).isEqualTo("default");
            assertThat(artifact.communityId()).isEqualTo(1L);
            assertThat(artifact.typeId()).isEqualTo(1L);
            assertThat(artifact.title()).isEqualTo("Resilience patterns talk");
            assertThat(artifact.description()).isEqualTo("How we handle retries and timeouts");
            assertThat(artifact.url()).isEqualTo("https://example.com/talk");
            assertThat(artifact.contributorUserId()).isEqualTo("dev-user");
            assertThat(artifact.provenanceSessionId()).isEqualTo(42L);
            assertThat(artifact.provenanceSegmentId()).isEqualTo(3L);
            assertThat(artifact.provenanceLabel()).isEqualTo("Talk");
            assertThat(artifact.status()).isEqualTo("ACTIVE");
            assertThat(artifact.searchDocument()).startsWith("Resilience patterns talk");
            assertThat(artifact.loveCount()).isEqualTo(5);
            assertThat(artifact.tags()).containsExactly("kafka", "ddd");
            assertThat(artifact.createdAt()).isEqualTo(CREATED);
            assertThat(artifact.updatedAt()).isEqualTo(UPDATED);
            assertThat(artifact.version()).isEqualTo(2L);
        });
    }

    @Test
    void find_answersEmptyForAnUnknownArtifact() {

        // given
        when(artifactRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.empty());

        // when / then
        assertThat(sut().find("default", 7L)).isEmpty();
    }

    @Test
    void find_keepsAMissingTagCollectionMissing() {

        // given
        when(artifactRepository.findByOrganizationIdAndId("default", 7L))
                .thenReturn(Optional.of(ArtifactEntityMother.any().id(7L).tags(null).build()));

        // when / then
        assertThat(sut().find("default", 7L)).get().extracting(StoredArtifact::tags).isNull();
    }

    @Test
    void save_writesEveryColumnAndAnswersWhatWasStored() {

        // given
        var artifact = JdbcArtifactStore.toArtifact(fullEntity());
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut().save(artifact);

        // then
        var written = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().ignoringFields("tags").isEqualTo(fullEntity());
        assertThat(written.getValue().getTags()).extracting(ArtifactTagRef::getTag).containsExactly("kafka", "ddd");
        assertThat(actual).isEqualTo(artifact);
    }

    @Test
    void save_writesNoTagCollectionWhenTheArtifactHasNone() {

        // given
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut().save(StoredArtifact.builder().title("Untagged").build());

        // then
        var written = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(written.capture());
        assertThat(written.getValue().getTags()).isNull();
    }

    @Test
    void delete_carriesTheIdAndVersion() {

        // when
        sut().delete(StoredArtifact.builder().id(7L).version(3L).tags(Set.of()).build());

        // then
        var deleted = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).delete(deleted.capture());
        assertThat(deleted.getValue().getId()).isEqualTo(7L);
        assertThat(deleted.getValue().getVersion()).isEqualTo(3L);
    }

    @Test
    void search_mapsThePageAndKeepsItsTotal() {

        // given
        var filter = ArtifactFilter.builder().q("kafka").build();
        var pageable = PageRequest.of(1, 2);
        when(artifactRepository.search("default", 1L, filter, pageable))
                .thenReturn(new PageImpl<>(List.of(fullEntity()), pageable, 9));

        // when
        var actual = sut().search("default", 1L, filter, pageable);

        // then
        assertThat(actual.getContent()).extracting(StoredArtifact::id).containsExactly(7L);
        assertThat(actual.getTotalElements()).isEqualTo(9);
        assertThat(actual.getPageable()).isEqualTo(pageable);
    }

    @Test
    void sessionsAndStats_answerWhatTheRepositoryReads() {

        // given
        var sessions = List.of(ArtifactSession.builder().sessionId(42L).count(2).build());
        var stats = LibraryStats.builder().total(3).sessionCount(1).capturedThisMonth(2).build();
        when(artifactRepository.sessionsWithArtifacts("default", 1L)).thenReturn(sessions);
        when(artifactRepository.stats("default", 1L)).thenReturn(stats);

        // when / then
        assertThat(sut().sessionsWithArtifacts("default", 1L)).isEqualTo(sessions);
        assertThat(sut().stats("default", 1L)).isEqualTo(stats);
    }
}
