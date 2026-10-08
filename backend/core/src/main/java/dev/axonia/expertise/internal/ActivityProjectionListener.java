package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

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
import lombok.RequiredArgsConstructor;

/**
 * Projects activity events from the {@code events} and {@code knowledge} modules into the expertise signal table.
 *
 * <p>
 * Each handler maps an inbound domain event to a {@link SignalWriter#replaceSource} or
 * {@link SignalWriter#deleteSource} call. When a replace would produce zero rows (empty tags list), a delete is issued
 * instead to avoid leaving stale signals behind.
 *
 * <p>
 * <strong>OrganizationContext coupling:</strong> the upstream events do not carry an {@code organizationId} — org is
 * resolved via {@link OrganizationContext} which reads the JWT claim set by the request thread. This is safe while
 * {@code @EnableAsync} is OFF, because {@link ApplicationModuleListener @ApplicationModuleListener} dispatches
 * synchronously on the same request thread where the JWT is present. If async event processing is ever enabled, the org
 * must be propagated into the event payload and set explicitly here — see {@code ArtifactLoveCountListener} for the
 * identical pattern and warning.
 */
@Component
@RequiredArgsConstructor
class ActivityProjectionListener {

    private final SignalWriter signalWriter;
    private final OrganizationContext organizationContext;

    @ApplicationModuleListener
    void on(TopicTagsChanged event) {

        var org = organizationContext.organizationId();
        if (event.kind() != TopicKind.OFFER || event.tags().isEmpty()) {
            signalWriter.deleteSource(org, SourceType.TOPIC, event.topicId());
            return;
        }
        var rows = offerRows(event.proposerUserId(), event.tags());
        signalWriter.replaceSource(new SignalSource(org, event.communityId(), SourceType.TOPIC, event.topicId()), rows);
    }

    @ApplicationModuleListener
    void on(TopicRemoved event) {

        signalWriter.deleteSource(organizationContext.organizationId(), SourceType.TOPIC, event.topicId());
    }

    @ApplicationModuleListener
    void on(SlotPresented event) {

        var org = organizationContext.organizationId();
        if (event.tags().isEmpty()) {
            signalWriter.deleteSource(org, SourceType.SLOT, event.slotId());
            return;
        }
        var rows = presentedRows(event.presenterUserIds(), event.tags());
        signalWriter.replaceSource(new SignalSource(org, event.communityId(), SourceType.SLOT, event.slotId()), rows);
    }

    @ApplicationModuleListener
    void on(SlotCleared event) {

        signalWriter.deleteSource(organizationContext.organizationId(), SourceType.SLOT, event.slotId());
    }

    @ApplicationModuleListener
    void on(ArtifactContributed event) {

        var org = organizationContext.organizationId();
        if (event.tags().isEmpty()) {
            signalWriter.deleteSource(org, SourceType.ARTIFACT, event.artifactId());
            return;
        }
        var rows = contributedRows(event.contributorUserId(), event.tags());
        signalWriter.replaceSource(new SignalSource(org, event.communityId(), SourceType.ARTIFACT, event.artifactId()),
                rows);
    }

    @ApplicationModuleListener
    void on(ArtifactWithdrawn event) {

        signalWriter.deleteSource(organizationContext.organizationId(), SourceType.ARTIFACT, event.artifactId());
    }

    @ApplicationModuleListener
    void on(ActionItemClosed event) {

        var org = organizationContext.organizationId();
        if (event.assigneeUserId() == null || event.tags().isEmpty()) {
            signalWriter.deleteSource(org, SourceType.ACTION_ITEM, event.actionItemId());
            return;
        }
        var rows = completedRows(event.assigneeUserId(), event.tags());
        signalWriter.replaceSource(
                new SignalSource(org, event.communityId(), SourceType.ACTION_ITEM, event.actionItemId()), rows);
    }

    @ApplicationModuleListener
    void on(ActionItemReopened event) {

        // Reopening withdraws the credit close granted: drop the same ACTION_ITEM source the close projected.
        signalWriter.deleteSource(organizationContext.organizationId(), SourceType.ACTION_ITEM, event.actionItemId());
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
