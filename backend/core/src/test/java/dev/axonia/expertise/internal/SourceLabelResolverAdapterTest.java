package dev.axonia.expertise.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.events.ActivityTitleReader;
import dev.axonia.expertise.SourceRef;
import dev.axonia.expertise.SourceType;
import dev.axonia.knowledge.ArtifactTitleReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

/**
 * Mocks carry no transaction, so nothing here can prove the rollback-only bug this adapter was rewritten for - the
 * previous version of this test passed while every expertise search over a withdrawn artifact answered 500. That proof
 * lives in {@code OrphanedEvidenceIntegrationTest}, against a real transaction manager. What is left to assert here is
 * the shape: one batched call per source type, and a miss that is a value rather than a throw.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class SourceLabelResolverAdapterTest {

    @Mock
    private ActivityTitleReader activityTitleReader;

    @Mock
    private ArtifactTitleReader artifactTitleReader;

    @InjectMocks
    private SourceLabelResolverAdapter sut;

    @Test
    void titlesFor_resolvesEachSourceTypeViaItsOwner() {

        // given
        when(activityTitleReader.slotTitles(List.of(1L))).thenReturn(Map.of(1L, "Kafka talk"));
        when(activityTitleReader.topicTitles(List.of(2L))).thenReturn(Map.of(2L, "Kafka backlog topic"));
        when(artifactTitleReader.titlesByIds(List.of(3L))).thenReturn(Map.of(3L, "Kafka playbook"));

        // when
        var titles = sut.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L), new SourceRef(SourceType.TOPIC, 2L),
                new SourceRef(SourceType.ARTIFACT, 3L)));

        // then
        assertThat(titles).containsOnly(entry(new SourceRef(SourceType.SLOT, 1L), "Kafka talk"),
                entry(new SourceRef(SourceType.TOPIC, 2L), "Kafka backlog topic"),
                entry(new SourceRef(SourceType.ARTIFACT, 3L), "Kafka playbook"));
    }

    @Test
    void titlesFor_batchesEachSourceTypeIntoOneCall() {

        // given
        when(activityTitleReader.slotTitles(List.of(1L, 4L))).thenReturn(Map.of(1L, "Talk A", 4L, "Talk B"));
        when(artifactTitleReader.titlesByIds(List.of(3L, 5L))).thenReturn(Map.of(3L, "Playbook", 5L, "Runbook"));

        // when
        var titles = sut.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L), new SourceRef(SourceType.ARTIFACT, 3L),
                new SourceRef(SourceType.SLOT, 4L), new SourceRef(SourceType.ARTIFACT, 5L)));

        // then
        assertThat(titles).containsOnly(entry(new SourceRef(SourceType.SLOT, 1L), "Talk A"),
                entry(new SourceRef(SourceType.SLOT, 4L), "Talk B"),
                entry(new SourceRef(SourceType.ARTIFACT, 3L), "Playbook"),
                entry(new SourceRef(SourceType.ARTIFACT, 5L), "Runbook"));
    }

    @Test
    void titlesFor_omitsSourcesThatNoLongerResolve() {

        // given
        when(activityTitleReader.slotTitles(List.of(1L))).thenReturn(Map.of());
        when(activityTitleReader.topicTitles(List.of(2L))).thenReturn(Map.of());
        when(artifactTitleReader.titlesByIds(List.of(3L))).thenReturn(Map.of());

        // when
        var titles = sut.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L), new SourceRef(SourceType.TOPIC, 2L),
                new SourceRef(SourceType.ARTIFACT, 3L)));

        // then
        assertThat(titles).isEmpty();
    }

    @Test
    void titlesFor_dedupesRepeatedRefs() {

        // given
        when(artifactTitleReader.titlesByIds(List.of(3L))).thenReturn(Map.of(3L, "Kafka playbook"));

        // when
        var titles = sut
                .titlesFor(List.of(new SourceRef(SourceType.ARTIFACT, 3L), new SourceRef(SourceType.ARTIFACT, 3L)));

        // then
        assertThat(titles).containsOnly(entry(new SourceRef(SourceType.ARTIFACT, 3L), "Kafka playbook"));
    }
}
