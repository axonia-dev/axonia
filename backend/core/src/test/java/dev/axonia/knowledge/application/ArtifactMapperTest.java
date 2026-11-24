package dev.axonia.knowledge.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.api.ArtifactStatus;
import dev.axonia.knowledge.api.Provenance;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.application.port.out.SessionLabels;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.SessionLabel;
import dev.axonia.knowledge.domain.StoredArtifactMother;
import dev.axonia.knowledge.domain.StoredArtifactType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactMapperTest {

    @Mock
    private ArtifactTypeStore artifactTypeStore;

    @Mock
    private SessionLabels sessionLabels;

    @InjectMocks
    private ArtifactMapper sut;

    @Test
    void mapsTypeLabelTagsAndStatus() {

        // given
        when(artifactTypeStore.findAll("default", Set.of(1L)))
                .thenReturn(List.of(StoredArtifactType.builder().id(1L).label("Recording").build()));
        var entity = StoredArtifactMother.any().id(10L).loveCount(7).tags(Set.of("b", "a")).build();

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
        when(artifactTypeStore.findAll("default", Set.of(1L)))
                .thenReturn(List.of(StoredArtifactType.builder().id(1L).label("Recording").build()));
        var entity = StoredArtifactMother.any().provenanceSessionId(55L).provenanceSegmentId(2L)
                .provenanceLabel("Frontend Dojo").build();

        // when
        var provenance = sut.toArtifact(entity).provenance();

        // then
        assertThat(provenance.sessionId()).isEqualTo(55L);
        assertThat(provenance.segmentId()).isEqualTo(2L);
        assertThat(provenance.label()).isEqualTo("Frontend Dojo");
        assertThat(provenance.seriesTitle()).isNull();
        assertThat(provenance.heldOn()).isNull();
    }

    @Test
    void toArtifacts_readsTheSeriesAndDayOfEveryProvenanceSessionInOneLookup() {

        // given
        when(artifactTypeStore.findAll("default", Set.of(1L))).thenReturn(List.of());
        when(sessionLabels.of(Set.of(55L, 66L))).thenReturn(Map.of(55L,
                SessionLabel.builder().name("Kafka night").seriesTitle("Platform evenings")
                        .heldOn(LocalDate.of(2026, 3, 2)).build(),
                66L, SessionLabel.builder().name("Session").heldOn(LocalDate.of(2026, 4, 1)).build()));
        var inASeries = StoredArtifactMother.any().id(1L).provenanceSessionId(55L).build();
        var oneOff = StoredArtifactMother.any().id(2L).provenanceSessionId(66L).build();
        var standalone = StoredArtifactMother.any().id(3L).build();

        // when
        var artifacts = sut.toArtifacts(List.of(inASeries, oneOff, standalone));

        // then
        assertThat(artifacts.get(0).provenance()).extracting(Provenance::seriesTitle, Provenance::heldOn)
                .containsExactly("Platform evenings", LocalDate.of(2026, 3, 2));
        assertThat(artifacts.get(1).provenance()).extracting(Provenance::seriesTitle, Provenance::heldOn)
                .containsExactly(null, LocalDate.of(2026, 4, 1));
        assertThat(artifacts.get(2).provenance()).isNull();
    }

    @Test
    void toSessions_labelsEachSessionNewestHeldFirstAndSinksAGoneOne() {

        // given
        var older = ArtifactSession.builder().sessionId(1L).count(2).build();
        var gone = ArtifactSession.builder().sessionId(2L).count(1).build();
        var newer = ArtifactSession.builder().sessionId(3L).count(4).build();
        var sameDayLaterId = ArtifactSession.builder().sessionId(4L).count(1).build();
        when(sessionLabels.of(List.of(1L, 2L, 3L, 4L))).thenReturn(
                Map.of(1L, SessionLabel.builder().name("Kafka night").heldOn(LocalDate.of(2026, 1, 8)).build(), 3L,
                        SessionLabel.builder().name("Rust intro").heldOn(LocalDate.of(2026, 5, 7)).build(), 4L,
                        SessionLabel.builder().name("Rust follow-up").heldOn(LocalDate.of(2026, 5, 7)).build()));

        // when
        var sessions = sut.toSessions(List.of(older, gone, newer, sameDayLaterId));

        // then
        assertThat(sessions).extracting(ArtifactSession::sessionId, ArtifactSession::name, ArtifactSession::count)
                .containsExactly(tuple(4L, "Rust follow-up", 1L), tuple(3L, "Rust intro", 4L),
                        tuple(1L, "Kafka night", 2L), tuple(2L, null, 1L));
    }

    @Test
    void toleratesNullTagsAndUnknownType() {

        // given
        when(artifactTypeStore.findAll("default", Set.of(1L))).thenReturn(List.of());
        var entity = StoredArtifactMother.any().tags(null).build();

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
        when(artifactTypeStore.findAll("default", Set.of(1L, 2L)))
                .thenReturn(List.of(StoredArtifactType.builder().id(1L).label("Recording").build(),
                        StoredArtifactType.builder().id(2L).label("Slides").build()));
        var typed1 = StoredArtifactMother.any().id(1L).typeId(1L).tags(Set.of()).build();
        var typed2 = StoredArtifactMother.any().id(2L).typeId(2L).tags(Set.of()).build();
        var untyped = StoredArtifactMother.any().id(3L).typeId(null).tags(Set.of()).build();

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
        verifyNoInteractions(artifactTypeStore);
    }
}
