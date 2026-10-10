package dev.axonia.expertise.adapter.in.event;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import dev.axonia.events.api.ActionItemClosed;
import dev.axonia.events.api.ActionItemReopened;
import dev.axonia.events.api.SlotCleared;
import dev.axonia.events.api.SlotPresented;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicRemoved;
import dev.axonia.events.api.TopicTagsChanged;
import dev.axonia.expertise.application.ActivitySignals;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.knowledge.api.ArtifactContributed;
import dev.axonia.knowledge.api.ArtifactWithdrawn;
import lombok.RequiredArgsConstructor;

/**
 * Projects activity events from the {@code events} and {@code knowledge} modules into the expertise signal table.
 *
 * <p>
 * Each handler maps an inbound domain event to a {@link ActivitySignals#replaceSource} or
 * {@link ActivitySignals#deleteSource} call. When a replace would produce zero rows (empty tags list), a delete is
 * issued instead to avoid leaving stale signals behind.
 *
 * <p>
 * The upstream events carry no {@code organizationId}; {@link ActivitySignals} resolves it from the request, which is
 * safe while {@link ApplicationModuleListener @ApplicationModuleListener} dispatches synchronously on the request
 * thread. See {@code ArtifactLoveCountListener} for the identical pattern and warning.
 */
@Component
@RequiredArgsConstructor
class ActivityProjectionListener {

    private final ActivitySignals activitySignals;

    @ApplicationModuleListener
    void on(TopicTagsChanged event) {

        if (event.kind() != TopicKind.OFFER || event.tags().isEmpty()) {
            activitySignals.deleteSource(SourceType.TOPIC, event.topicId());
            return;
        }
        var rows = offerRows(event.proposerUserId(), event.tags());
        activitySignals.replaceSource(event.communityId(), SourceType.TOPIC, event.topicId(), rows);
    }

    @ApplicationModuleListener
    void on(TopicRemoved event) {

        activitySignals.deleteSource(SourceType.TOPIC, event.topicId());
    }

    @ApplicationModuleListener
    void on(SlotPresented event) {

        if (event.tags().isEmpty()) {
            activitySignals.deleteSource(SourceType.SLOT, event.slotId());
            return;
        }
        var rows = presentedRows(event.presenterUserIds(), event.tags());
        activitySignals.replaceSource(event.communityId(), SourceType.SLOT, event.slotId(), rows);
    }

    @ApplicationModuleListener
    void on(SlotCleared event) {

        activitySignals.deleteSource(SourceType.SLOT, event.slotId());
    }

    @ApplicationModuleListener
    void on(ArtifactContributed event) {

        if (event.tags().isEmpty()) {
            activitySignals.deleteSource(SourceType.ARTIFACT, event.artifactId());
            return;
        }
        var rows = contributedRows(event.contributorUserId(), event.tags());
        activitySignals.replaceSource(event.communityId(), SourceType.ARTIFACT, event.artifactId(), rows);
    }

    @ApplicationModuleListener
    void on(ArtifactWithdrawn event) {

        activitySignals.deleteSource(SourceType.ARTIFACT, event.artifactId());
    }

    @ApplicationModuleListener
    void on(ActionItemClosed event) {

        if (event.assigneeUserId() == null || event.tags().isEmpty()) {
            activitySignals.deleteSource(SourceType.ACTION_ITEM, event.actionItemId());
            return;
        }
        var rows = completedRows(event.assigneeUserId(), event.tags());
        activitySignals.replaceSource(event.communityId(), SourceType.ACTION_ITEM, event.actionItemId(), rows);
    }

    @ApplicationModuleListener
    void on(ActionItemReopened event) {

        // Reopening withdraws the credit close granted: drop the same ACTION_ITEM source the close projected.
        activitySignals.deleteSource(SourceType.ACTION_ITEM, event.actionItemId());
    }

    private List<SignalRow> offerRows(String userId, List<String> tags) {

        var now = Instant.now();
        return tags.stream().map(tag -> new SignalRow(userId, tag, SignalType.OFFERED, now))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<SignalRow> presentedRows(List<String> presenterUserIds, List<String> tags) {

        var now = Instant.now();
        return presenterUserIds.stream()
                .flatMap(
                        presenter -> tags.stream().map(tag -> new SignalRow(presenter, tag, SignalType.PRESENTED, now)))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<SignalRow> contributedRows(String userId, List<String> tags) {

        var now = Instant.now();
        return tags.stream().map(tag -> new SignalRow(userId, tag, SignalType.CONTRIBUTED, now))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<SignalRow> completedRows(String userId, List<String> tags) {

        var now = Instant.now();
        return tags.stream().map(tag -> new SignalRow(userId, tag, SignalType.COMPLETED, now))
                .collect(Collectors.toUnmodifiableList());
    }
}
