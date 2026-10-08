package dev.axonia.events.internal;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.ActionItemOutcome;
import dev.axonia.events.Checkpoint;
import lombok.RequiredArgsConstructor;

/**
 * Resolves where an action item is answered. A non-null {@code checkpointSessionId} is pinned: an organizer named that
 * session, and it reads as such even once it is in the past. A null one late-binds to the community's soonest upcoming
 * session, unpinned - and when the community has none scheduled yet, to no session at all, still unpinned: null means
 * "the next session, whenever it is", never "no checkpoint".
 *
 * <p>
 * The zone governing the resolved session's wall clock is resolved here too, because only here is it known which
 * session a checkpoint actually points at: a pinned item and a late-bound one on the same page can land on sessions of
 * different series, in different zones.
 */
@Component
@RequiredArgsConstructor
public class CheckpointResolver {

    private final SessionRepository sessionRepository;
    private final SeriesRepository seriesRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Checkpoint resolve(String organizationId, ActionItemEntity item) {

        if (item.getCheckpointSessionId() != null) {
            var session = sessionRepository.findByOrganizationIdAndId(organizationId, item.getCheckpointSessionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Checkpoint session not found: " + item.getCheckpointSessionId()));
            return checkpointOf(organizationId, session, true);
        }
        if (!lateBinds(item)) {
            return Checkpoint.builder().pinned(false).build();
        }
        return sessionRepository.findSoonestUpcoming(organizationId, item.getCommunityId(), clock.instant())
                .map(session -> checkpointOf(organizationId, session, false))
                .orElseGet(() -> Checkpoint.builder().pinned(false).build());
    }

    /**
     * Only an open item is still waiting on a session, so only an open item late-binds to the next one. A settled item
     * names a session when an organizer pinned one, and otherwise names none.
     */
    private static boolean lateBinds(ActionItemEntity item) {

        return item.getCheckpointSessionId() == null && item.getOutcome() == ActionItemOutcome.OPEN;
    }

    /**
     * The batched form of {@link #resolve}, for a page of items that all belong to one community - {@code list} routes
     * are always scoped to a single community's slug, so this never has to resolve more than one "soonest upcoming"
     * session per call. At most three queries total, however many items are on the page: one for every distinct pinned
     * session, one for the community's own soonest upcoming session, only when at least one item on the page is
     * unpinned, and one for the zone of every series those sessions belong to. A per-item {@link #resolve} call in a
     * list loop is the fan-out this exists to avoid.
     */
    @Transactional(readOnly = true)
    public Map<Long, Checkpoint> resolveAll(String organizationId, long communityId,
            Collection<ActionItemEntity> items) {

        var pinnedSessionIds = items.stream().map(ActionItemEntity::getCheckpointSessionId).filter(Objects::nonNull)
                .distinct().toList();
        var pinnedSessions = pinnedSessionIds.isEmpty()
                ? Map.<Long, SessionEntity>of()
                : sessionRepository
                        .findByOrganizationIdAndCommunityIdAndIdIn(organizationId, communityId, pinnedSessionIds)
                        .stream().collect(Collectors.toMap(SessionEntity::getId, session -> session));

        var hasLateBinding = items.stream().anyMatch(CheckpointResolver::lateBinds);
        var soonest = hasLateBinding
                ? sessionRepository.findSoonestUpcoming(organizationId, communityId, clock.instant())
                : Optional.<SessionEntity>empty();

        var seriesZones = seriesZonesOf(organizationId,
                Stream.concat(pinnedSessions.values().stream(), soonest.stream()).toList());

        var checkpoints = new HashMap<Long, Checkpoint>();
        for (var item : items) {
            checkpoints.put(item.getId(), checkpointFor(item, pinnedSessions, soonest, seriesZones));
        }
        return checkpoints;
    }

    private Checkpoint checkpointFor(ActionItemEntity item, Map<Long, SessionEntity> pinnedSessions,
            Optional<SessionEntity> soonest, Map<Long, String> seriesZones) {

        if (item.getCheckpointSessionId() != null) {
            return pinnedCheckpoint(item, pinnedSessions, seriesZones);
        }
        return lateBinds(item) ? unpinnedCheckpoint(soonest, seriesZones) : Checkpoint.builder().pinned(false).build();
    }

    private Checkpoint pinnedCheckpoint(ActionItemEntity item, Map<Long, SessionEntity> pinnedSessions,
            Map<Long, String> seriesZones) {

        var session = pinnedSessions.get(item.getCheckpointSessionId());
        if (session == null) {
            throw new IllegalStateException("Checkpoint session not found: " + item.getCheckpointSessionId());
        }
        return checkpoint(session, true, seriesZones);
    }

    private Checkpoint unpinnedCheckpoint(Optional<SessionEntity> soonest, Map<Long, String> seriesZones) {

        return soonest.map(session -> checkpoint(session, false, seriesZones))
                .orElseGet(() -> Checkpoint.builder().pinned(false).build());
    }

    private Checkpoint checkpointOf(String organizationId, SessionEntity session, boolean pinned) {

        return checkpoint(session, pinned, seriesZonesOf(organizationId, List.of(session)));
    }

    private Checkpoint checkpoint(SessionEntity session, boolean pinned, Map<Long, String> seriesZones) {

        return Checkpoint.builder().sessionId(session.getId()).scheduledAt(session.getScheduledAt()).pinned(pinned)
                .governingTimeZone(governingZoneOf(session, seriesZones)).build();
    }

    /**
     * An occurrence reads the zone of the series it belongs to. A one-off has no governing zone at all: its stored
     * {@code timeZone} is only whichever clock its creator happened to be on ({@code SessionService} takes it from the
     * request), so reading a checkpoint on it would name a different day from the session card and the picker the
     * organizer chose the session from, both of which read a one-off on the viewer's own zone. Null means the client
     * supplies the viewer's.
     */
    private String governingZoneOf(SessionEntity session, Map<Long, String> seriesZones) {

        return session.getSeriesId() == null ? null : seriesZones.get(session.getSeriesId());
    }

    /**
     * The zone of every series these sessions belong to, in one query for the whole batch - a page resolving to
     * sessions of several series must not cost a read per row. A page of nothing but one-offs asks nothing at all.
     */
    private Map<Long, String> seriesZonesOf(String organizationId, Collection<SessionEntity> sessions) {

        var seriesIds = sessions.stream().map(SessionEntity::getSeriesId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        return seriesRepository.findByOrganizationIdAndIdIn(organizationId, seriesIds).stream()
                .filter(series -> series.getTimeZone() != null)
                .collect(Collectors.toMap(SeriesEntity::getId, SeriesEntity::getTimeZone));
    }
}
