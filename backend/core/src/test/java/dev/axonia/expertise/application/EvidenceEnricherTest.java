package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.SourceLabelResolver;
import dev.axonia.expertise.domain.CommunitySummary;
import dev.axonia.expertise.domain.Evidence;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseSignalMother;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceRef;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.recognition.api.ArtifactBadge;
import dev.axonia.recognition.api.SlotSource;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceEnricherTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private SourceLabelResolver sourceLabelResolver;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private SlotSourceAccess slotSourceAccess;

    @Mock
    private ArtifactTypeAccess artifactTypeAccess;

    @Captor
    private ArgumentCaptor<Collection<Long>> communityIds;

    @InjectMocks
    private EvidenceEnricher sut;

    @BeforeEach
    void noBadgesByDefault() {

        when(slotSourceAccess.slotSources(any())).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(any())).thenReturn(Map.of());
    }

    private static Evidence evidenceWith(EvidenceItem item) {

        return Evidence.builder().presentedCount(1).contributedCount(0).offeredCount(0).recent(List.of(item)).build();
    }

    private static EvidenceItem slotItem() {

        return EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT).sourceId(1L)
                .label("").communitySlug(null).occurredAt(WHEN).build();
    }

    private static EvidenceItem itemOf(SourceType sourceType, Long sourceId) {

        return EvidenceItem.builder().signalType(SignalType.CONTRIBUTED).sourceType(sourceType).sourceId(sourceId)
                .label("").communitySlug(null).occurredAt(WHEN).build();
    }

    @Test
    void enrich_fillsTitleAndSlug() {

        // given
        var signal = ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(1L).communityId(10L).build();
        when(sourceLabelResolver.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L))))
                .thenReturn(Map.of(new SourceRef(SourceType.SLOT, 1L), "Kafka talk"));
        when(communityAccess.summaryById(List.of(10L)))
                .thenReturn(Map.of(10L, new CommunitySummary("eng", "Eng Guild")));

        // when
        var result = sut.enrich(evidenceWith(slotItem()), List.of(signal));

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Kafka talk");
            assertThat(item.communitySlug()).isEqualTo("eng");
        });
        assertThat(result.presentedCount()).isEqualTo(1);
    }

    @Test
    void enrich_unresolvedSource_leavesTitleAndSlugNull() {

        // given
        var signal = ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(1L).communityId(10L).build();
        when(sourceLabelResolver.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L)))).thenReturn(Map.of());
        when(communityAccess.summaryById(List.of(10L))).thenReturn(Map.of());

        // when
        var result = sut.enrich(evidenceWith(slotItem()), List.of(signal));

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.label()).isNull();
            assertThat(item.communitySlug()).isNull();
        });
    }

    @Test
    void enrich_itemWithoutMatchingSignal_leavesSlugNull() {

        // given - evidence references a source with no signal in the set (so no community mapping)
        when(sourceLabelResolver.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L))))
                .thenReturn(Map.of(new SourceRef(SourceType.SLOT, 1L), "Orphan talk"));
        when(communityAccess.summaryById(List.of())).thenReturn(Map.of());

        // when
        var result = sut.enrich(evidenceWith(slotItem()), List.of());

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Orphan talk");
            assertThat(item.communitySlug()).isNull();
        });
    }

    @Test
    void enrichItems_fillsTitleAndSlug() {

        // given
        var signal = ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(1L).communityId(10L).build();
        when(sourceLabelResolver.titlesFor(List.of(new SourceRef(SourceType.SLOT, 1L))))
                .thenReturn(Map.of(new SourceRef(SourceType.SLOT, 1L), "Kafka talk"));
        when(communityAccess.summaryById(List.of(10L)))
                .thenReturn(Map.of(10L, new CommunitySummary("eng", "Eng Guild")));

        // when
        var result = sut.enrichItems(List.of(slotItem()), List.of(signal));

        // then
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Kafka talk");
            assertThat(item.communitySlug()).isEqualTo("eng");
        });
    }

    @Test
    void enrichItems_returnsOneItemPerInput_inInputOrder() {

        // given - an order no natural sort would produce, and a source that appears twice
        var items = List.of(itemOf(SourceType.ARTIFACT, 2L), itemOf(SourceType.ARTIFACT, 1L),
                itemOf(SourceType.ARTIFACT, 3L), itemOf(SourceType.ARTIFACT, 2L));
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of(new SourceRef(SourceType.ARTIFACT, 1L), "one",
                new SourceRef(SourceType.ARTIFACT, 2L), "two", new SourceRef(SourceType.ARTIFACT, 3L), "three"));
        when(communityAccess.summaryById(any())).thenReturn(Map.of());

        // when
        var result = sut.enrichItems(items, List.of());

        // then - callers join their own list to this one by index, so length and order are the contract
        assertThat(result).extracting(EvidenceItem::sourceId).containsExactly(2L, 1L, 3L, 2L);
        assertThat(result).extracting(EvidenceItem::label).containsExactly("two", "one", "three", "two");
    }

    @Test
    void enrichItems_namesEachCommunityToTheDirectoryOnce() {

        // given - three sources that all belong to the same community
        var signals = List.of(
                ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(1L).communityId(10L).build(),
                ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(2L).communityId(10L).build(),
                ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(3L).communityId(10L).build());
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of());

        // when
        sut.enrichItems(List.of(itemOf(SourceType.SLOT, 1L), itemOf(SourceType.SLOT, 2L), itemOf(SourceType.SLOT, 3L)),
                signals);

        // then
        verify(communityAccess).summaryById(communityIds.capture());
        assertThat(communityIds.getValue()).containsExactly(10L);
    }

    @Test
    void enrich_emptyEvidence_returnsUnchanged() {

        // given
        var empty = Evidence.builder().presentedCount(0).contributedCount(0).offeredCount(0).recent(List.of()).build();

        // then
        assertThat(sut.enrich(empty, List.of())).isEqualTo(empty);
    }

    @Test
    void enrich_slot_addsSessionAndSectionTypeBadge() {

        // given
        var signal = ExpertiseSignalMother.any().sourceType(SourceType.SLOT).sourceId(1L).communityId(10L).build();
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of(new SourceRef(SourceType.SLOT, 1L), "Kafka talk"));
        when(communityAccess.summaryById(any())).thenReturn(Map.of(10L, new CommunitySummary("eng", "Eng Guild")));
        when(slotSourceAccess.slotSources(List.of(1L)))
                .thenReturn(Map.of(1L, new SlotSource(42L, 7L, "Workshop", "wrench", "#123456")));

        // when
        var result = sut.enrich(evidenceWith(slotItem()), List.of(signal));

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.sessionId()).isEqualTo(42L);
            assertThat(item.typeId()).isEqualTo(7L);
            assertThat(item.typeLabel()).isEqualTo("Workshop");
            assertThat(item.typeIcon()).isEqualTo("wrench");
            assertThat(item.typeAccentColor()).isEqualTo("#123456");
        });
    }

    @Test
    void enrich_artifact_addsTypeBadgeButNoSession() {

        // given
        var artifact = itemOf(SourceType.ARTIFACT, 2L);
        var signal = ExpertiseSignalMother.any().sourceType(SourceType.ARTIFACT).sourceId(2L).communityId(10L).build();
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of());
        when(artifactTypeAccess.artifactTypes(List.of(2L)))
                .thenReturn(Map.of(2L, new ArtifactBadge(3L, "RFC", "file-text", "#abcdef")));

        // when
        var result = sut.enrich(evidenceWith(artifact), List.of(signal));

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.sessionId()).isNull();
            assertThat(item.typeId()).isEqualTo(3L);
            assertThat(item.typeLabel()).isEqualTo("RFC");
            assertThat(item.typeIcon()).isEqualTo("file-text");
            assertThat(item.typeAccentColor()).isEqualTo("#abcdef");
        });
    }

    @Test
    void enrich_artifactWithoutResolvableType_leavesBadgeNull() {

        // given - artifactTypes returns empty (default stub)
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of());

        // when
        var result = sut.enrich(evidenceWith(itemOf(SourceType.ARTIFACT, 2L)), List.of());

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> assertThat(item.typeId()).isNull());
    }

    @Test
    void enrich_topic_leavesBadgeAndSessionNull() {

        // given
        when(sourceLabelResolver.titlesFor(any())).thenReturn(Map.of());
        when(communityAccess.summaryById(any())).thenReturn(Map.of());

        // when
        var result = sut.enrich(evidenceWith(itemOf(SourceType.TOPIC, 3L)), List.of());

        // then
        assertThat(result.recent()).singleElement().satisfies(item -> {
            assertThat(item.sessionId()).isNull();
            assertThat(item.typeId()).isNull();
        });
    }
}
