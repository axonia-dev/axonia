package dev.axonia.expertise.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.events.ActionItemClosed;
import dev.axonia.events.ActionItemReopened;
import dev.axonia.events.SlotCleared;
import dev.axonia.events.SlotPresented;
import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicRemoved;
import dev.axonia.events.TopicTagsChanged;
import dev.axonia.expertise.OrganizationContext;
import dev.axonia.expertise.SignalType;
import dev.axonia.expertise.SourceType;
import dev.axonia.expertise.internal.SignalWriter.SignalRow;
import dev.axonia.expertise.internal.SignalWriter.SignalSource;
import dev.axonia.knowledge.ArtifactContributed;
import dev.axonia.knowledge.ArtifactWithdrawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityProjectionListenerTest {

    private static final String ORG = "default";

    @Mock
    private SignalWriter signalWriter;

    @Mock
    private OrganizationContext organizationContext;

    private ActivityProjectionListener listener;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        listener = new ActivityProjectionListener(signalWriter, organizationContext);
    }

    // ── TopicTagsChanged ────────────────────────────────────────────────────

    @Test
    void topicTagsChanged_offerKind_replacesSourceWithOfferedSignals() {

        var event = new TopicTagsChanged(10L, 1L, "alice", TopicKind.OFFER, List.of("java", "spring"));

        listener.on(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SignalRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalWriter).replaceSource(eq(new SignalSource(ORG, 1L, SourceType.TOPIC, 10L)), captor.capture());

        var rows = captor.getValue();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.userId()).isEqualTo("alice");
            assertThat(row.signalType()).isEqualTo(SignalType.OFFERED);
            assertThat(row.occurredAt()).isNotNull();
        });
        assertThat(rows.stream().map(SignalRow::rawTag).toList()).containsExactlyInAnyOrder("java", "spring");
    }

    @Test
    void topicTagsChanged_requestKind_deletesSource() {

        var event = new TopicTagsChanged(10L, 1L, "alice", TopicKind.REQUEST, List.of("java"));

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.TOPIC, 10L);
    }

    @Test
    void topicTagsChanged_offerKindEmptyTags_deletesSource() {

        var event = new TopicTagsChanged(10L, 1L, "alice", TopicKind.OFFER, List.of());

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.TOPIC, 10L);
    }

    // ── TopicRemoved ────────────────────────────────────────────────────────

    @Test
    void topicRemoved_deletesSource() {

        var event = new TopicRemoved(20L, 1L);

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.TOPIC, 20L);
    }

    // ── SlotPresented ───────────────────────────────────────────────────────

    @Test
    void slotPresented_replacesSourceWithPresentedSignalsPerPresenterPerTag() {

        var event = new SlotPresented(30L, 1L, List.of("alice", "bob"), List.of("kafka", "arch"));

        listener.on(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SignalRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalWriter).replaceSource(eq(new SignalSource(ORG, 1L, SourceType.SLOT, 30L)), captor.capture());

        var rows = captor.getValue();
        // 2 presenters × 2 tags = 4 rows
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(row -> assertThat(row.signalType()).isEqualTo(SignalType.PRESENTED));
        assertThat(rows.stream().map(SignalRow::userId).distinct().toList()).containsExactlyInAnyOrder("alice", "bob");
        assertThat(rows.stream().map(SignalRow::rawTag).distinct().toList()).containsExactlyInAnyOrder("kafka", "arch");
    }

    @Test
    void slotPresented_emptyTags_deletesSource() {

        var event = new SlotPresented(30L, 1L, List.of("alice"), List.of());

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.SLOT, 30L);
    }

    // ── SlotCleared ─────────────────────────────────────────────────────────

    @Test
    void slotCleared_deletesSource() {

        var event = new SlotCleared(40L, 1L);

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.SLOT, 40L);
    }

    // ── ArtifactContributed ─────────────────────────────────────────────────

    @Test
    void artifactContributed_replacesSourceWithContributedSignalsPerTag() {

        var event = new ArtifactContributed(50L, 1L, "carol", List.of("testing", "java"));

        listener.on(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SignalRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalWriter).replaceSource(eq(new SignalSource(ORG, 1L, SourceType.ARTIFACT, 50L)), captor.capture());

        var rows = captor.getValue();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.userId()).isEqualTo("carol");
            assertThat(row.signalType()).isEqualTo(SignalType.CONTRIBUTED);
        });
        assertThat(rows.stream().map(SignalRow::rawTag).toList()).containsExactlyInAnyOrder("testing", "java");
    }

    @Test
    void artifactContributed_emptyTags_deletesSource() {

        var event = new ArtifactContributed(50L, 1L, "carol", List.of());

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.ARTIFACT, 50L);
    }

    // ── ArtifactWithdrawn ───────────────────────────────────────────────────

    @Test
    void artifactWithdrawn_deletesSource() {

        var event = new ArtifactWithdrawn(60L, 1L);

        listener.on(event);

        verify(signalWriter).deleteSource(ORG, SourceType.ARTIFACT, 60L);
    }

    // ── ActionItemClosed ────────────────────────────────────────────────────

    @Test
    void closingAnAssignedActionItemWritesCompletedSignals() {

        listener.on(new ActionItemClosed(7L, 1L, "bob", List.of("green-it", "carbon")));

        var source = ArgumentCaptor.forClass(SignalSource.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SignalRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(signalWriter).replaceSource(source.capture(), rows.capture());
        assertThat(source.getValue().sourceType()).isEqualTo(SourceType.ACTION_ITEM);
        assertThat(source.getValue().sourceId()).isEqualTo(7L);
        assertThat(source.getValue().communityId()).isEqualTo(1L);
        assertThat(rows.getValue()).allMatch(r -> r.userId().equals("bob") && r.signalType() == SignalType.COMPLETED);
        assertThat(rows.getValue().stream().map(SignalRow::rawTag).toList()).containsExactlyInAnyOrder("green-it",
                "carbon");
    }

    @Test
    void closingAnUnassignedOrUntaggedActionItemCreditsNoOne() {

        listener.on(new ActionItemClosed(7L, 1L, null, List.of("green-it")));

        verify(signalWriter).deleteSource(ORG, SourceType.ACTION_ITEM, 7L);
    }

    @Test
    void closingAnAssignedActionItemWithNoTagsCreditsNoOne() {

        listener.on(new ActionItemClosed(7L, 1L, "bob", List.of()));

        verify(signalWriter).deleteSource(ORG, SourceType.ACTION_ITEM, 7L);
    }

    // ── ActionItemReopened ──────────────────────────────────────────────────

    @Test
    void reopeningAnActionItemWithdrawsItsCompletedCredit() {

        listener.on(new ActionItemReopened(7L, 1L));

        verify(signalWriter).deleteSource(ORG, SourceType.ACTION_ITEM, 7L);
    }
}
