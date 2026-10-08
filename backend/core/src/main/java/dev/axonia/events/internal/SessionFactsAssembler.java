package dev.axonia.events.internal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.axonia.PublicUrlProperties;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.events.CommunityAccess;
import dev.axonia.events.Session;
import dev.axonia.events.SessionResourceAccess;
import dev.axonia.events.UserProfileAccess;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Resolves everything {@link SessionFactsContext} needs and hands the result to {@link SessionFacts#of} - the one place
 * the three producers share this assembly, rather than each repeating the same lookups. Every lookup here takes
 * {@code organizationId} explicitly, never through {@code events}' own request-scoped {@code
 * OrganizationContext}: a producer runs inside the communication sweep's {@code OrganizationScope.runAs}, which carries
 * no JWT, so the implicit-organization overload of any of these ports would throw exactly the way
 * {@code MessageSubjectPolicyAdapterRunsWithNoJwtTest} already proved for the delivery gate's own path.
 */
@Component
@RequiredArgsConstructor
class SessionFactsAssembler {

    private static final DateTimeFormatter NEXT_SESSION_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
    private static final String BACKLOG_URL_TEMPLATE = "%s/communities/%s/backlog";

    private final SessionMapper sessionMapper;
    private final SeriesRepository seriesRepository;
    private final SessionRepository sessionRepository;
    private final CommunityAccess communityAccess;
    private final UserProfileAccess userProfileAccess;
    private final SessionResourceAccess sessionResourceAccess;
    private final ActionItemRepository actionItemRepository;
    private final ActionItemCheckpointRepository actionItemCheckpointRepository;
    private final PublicUrlProperties publicUrlProperties;

    /**
     * Facts for a notice that never mentions what follows this session - {@code SESSION_UPCOMING} and both arms of the
     * awaiting-close nudge. Action points and checked items are a wrap-up-only concern: resolving and persisting them
     * into a row that never renders them would be wasted work, and - since an assignee resolves to a display name, not
     * just an id - a needless widening of what personal data this fact map carries. Neither notice reads
     * {@code actionPoints} or {@code checkedItems}, so this overload never queries {@link ActionItemRepository} or
     * {@link ActionItemCheckpointRepository} at all.
     */
    Map<String, Object> facts(String organizationId, SessionEntity entity) {

        return facts(FactsRequest.builder().organizationId(organizationId).entity(entity)
                .series(series(organizationId, entity)).build());
    }

    /**
     * Facts for the wrap-up, which alone carries {@code nextSession} - the series' next occurrence after this one, when
     * there is one - {@code actionPoints} (what this session raised) and {@code checkedItems} (what this session
     * answered), resolved here because the wrap-up is the one message that renders them.
     */
    Map<String, Object> factsForWrapUp(String organizationId, SessionEntity entity, Instant now) {

        var series = series(organizationId, entity);
        var actionPointEntities = actionItemRepository.findByOriginSessionId(organizationId, entity.getCommunityId(),
                entity.getId());
        var checkedItems = checkedItems(organizationId, entity.getCommunityId(), entity.getId());
        return facts(FactsRequest.builder().organizationId(organizationId).entity(entity).series(series)
                .nextSession(nextSessionLabel(organizationId, entity, series, now))
                .actionPointEntities(actionPointEntities).checkedItems(checkedItems).build());
    }

    /**
     * The items THIS session answered, joined by hand against the {@link ActionItemEntity} each decision names: the
     * checkpoint log ({@link ActionItemCheckpointEntity}) carries an {@code actionItemId} and an outcome, never a title
     * or an assignee. Two queries, not a hand-written SQL join - the whole session's checkpoint rows come back in one
     * call, and the handful of items they name come back in a second, the same shape
     * {@link ActionItemRepositoryCustomImpl#hydrate} already uses for a page of items and their tags. A lookup miss
     * (not reachable today - {@code action_item_checkpoint_item_fkey} is {@code ON DELETE CASCADE}, never null) drops
     * that one row rather than failing the whole mail: one missing "What was checked" line is a smaller failure than a
     * wrap-up that never sends.
     */
    private List<CheckedItemSource> checkedItems(String organizationId, Long communityId, Long sessionId) {

        var checkpoints = actionItemCheckpointRepository.findByOrganizationIdAndCommunityIdAndSessionId(organizationId,
                communityId, sessionId);
        if (checkpoints.isEmpty()) {
            return List.of();
        }
        var itemIds = checkpoints.stream().map(ActionItemCheckpointEntity::getActionItemId).distinct().toList();
        var itemsById = actionItemRepository.findByOrganizationIdAndIdIn(organizationId, itemIds).stream()
                .collect(Collectors.toMap(ActionItemEntity::getId, Function.identity()));
        return checkpoints.stream().map(checkpoint -> toCheckedItemSource(checkpoint, itemsById))
                .filter(Objects::nonNull).toList();
    }

    private static CheckedItemSource toCheckedItemSource(ActionItemCheckpointEntity checkpoint,
            Map<Long, ActionItemEntity> itemsById) {

        var item = itemsById.get(checkpoint.getActionItemId());
        return item == null
                ? null
                : new CheckedItemSource(item.getTitle(), item.getAssigneeUserId(), checkpoint.getOutcome());
    }

    /**
     * Facts for the awaiting-close nudge, which alone carries {@code nudgeArm} - which of
     * {@link AwaitingCloseProducer}'s two firings enqueued this row. Everything else is identical to
     * {@link #facts(String, SessionEntity)}: a nudge mentions neither what follows the session nor its action points.
     */
    Map<String, Object> factsForAwaitingClose(String organizationId, SessionEntity entity, String nudgeArm) {

        return facts(FactsRequest.builder().organizationId(organizationId).entity(entity)
                .series(series(organizationId, entity)).nudgeArm(nudgeArm).build());
    }

    /**
     * Groups the private {@link #facts(FactsRequest)} method's inputs into one named parameter object rather than six
     * positional ones - PMD's {@code ExcessiveParameterList} trips at five (verified empirically: it reports at exactly
     * the configured minimum, not only above it), and a suppression is never the fix in this repo. {@code
     * actionPointEntities} defaults to an empty list rather than null, the same absent-state contract every other
     * optional collection here already uses.
     *
     * <p>
     * {@code series} is the row the session belongs to, read once per assembly and null for a one-off - or for a
     * session whose series row is gone. It carries two facts no caller may resolve twice: the title the message is
     * headed with, and the zone the community's own clock runs on.
     */
    @Builder
    private record FactsRequest(String organizationId, SessionEntity entity, SeriesEntity series, String nextSession,
            List<ActionItemEntity> actionPointEntities, List<CheckedItemSource> checkedItems, String nudgeArm) {

        private FactsRequest {
            actionPointEntities = actionPointEntities == null ? List.of() : actionPointEntities;
            checkedItems = checkedItems == null ? List.of() : checkedItems;
        }
    }

    private Map<String, Object> facts(FactsRequest request) {

        var session = sessionMapper.toSession(request.entity());
        var community = communityAccess.communityIdentity(request.organizationId(), request.entity().getCommunityId());
        var resources = sessionResourceAccess.resourcesForSession(request.organizationId(), request.entity().getId(),
                request.entity().getCommunityId());
        var context = SessionFactsContext.builder().sessionUrl(sessionUrl(community, request.entity()))
                .displayNamesByUserId(displayNames(request.organizationId(), session, request.actionPointEntities(),
                        request.checkedItems()))
                .seriesTitle(seriesTitle(request.series())).nextSession(request.nextSession())
                .resourcesBySlotId(resources.bySlot()).sessionResources(resources.sessionLevel())
                .actionPoints(actionPoints(request.actionPointEntities())).checkedItems(request.checkedItems())
                .community(community).communityId(request.entity().getCommunityId()).nudgeArm(request.nudgeArm())
                .backlogUrl(BACKLOG_URL_TEMPLATE.formatted(publicUrlProperties.publicBaseUrl(), community.slug()))
                .build();
        return SessionFacts.of(session, context);
    }

    private List<ActionPointSource> actionPoints(List<ActionItemEntity> actionPointEntities) {

        return actionPointEntities.stream().map(SessionFactsAssembler::toActionPointSource).toList();
    }

    private static ActionPointSource toActionPointSource(ActionItemEntity entity) {

        return new ActionPointSource(entity.getTitle(), entity.getAssigneeUserId(), entity.getDueDate(),
                entity.getOutcome());
    }

    private String sessionUrl(CommunityIdentity community, SessionEntity entity) {

        return SessionUrls.sessionBaseUrl(publicUrlProperties.publicBaseUrl(), community.slug()) + entity.getId();
    }

    /**
     * Resolves display names for every presenter across the session's slots, every action point's assignee and every
     * checked item's assignee, in one batched {@link UserProfileAccess#displayNames} call rather than a separate lookup
     * per concern. Assignees share this exact path - never a call of their own - so {@link SessionFacts} can drop an
     * unresolvable assignee id the same way it already drops an unresolvable presenter id: all three read the same map,
     * built the same way.
     */
    private Map<String, String> displayNames(String organizationId, Session session,
            List<ActionItemEntity> actionPointEntities, List<CheckedItemSource> checkedItems) {

        var presenterIds = session.sections().stream().flatMap(section -> section.slots().stream())
                .flatMap(slot -> slot.presenterUserIds().stream());
        var assigneeIds = actionPointEntities.stream().map(ActionItemEntity::getAssigneeUserId)
                .filter(Objects::nonNull);
        var checkedAssigneeIds = checkedItems.stream().map(CheckedItemSource::assigneeUserId).filter(Objects::nonNull);
        var ids = Stream.concat(Stream.concat(presenterIds, assigneeIds), checkedAssigneeIds).distinct().toList();
        return userProfileAccess.displayNames(organizationId, ids);
    }

    private SeriesEntity series(String organizationId, SessionEntity entity) {

        if (entity.getSeriesId() == null) {
            return null;
        }
        return seriesRepository.findByOrganizationIdAndId(organizationId, entity.getSeriesId()).orElse(null);
    }

    private static String seriesTitle(SeriesEntity series) {

        return series == null ? null : series.getTitle();
    }

    private String nextSessionLabel(String organizationId, SessionEntity entity, SeriesEntity series, Instant now) {

        if (entity.getSeriesId() == null) {
            return null;
        }
        return sessionRepository
                .findPendingForSeriesExcluding(organizationId, entity.getSeriesId(), entity.getId(), now)
                .map(next -> label(next, zoneOf(series))).orElse(null);
    }

    /**
     * The clock the community itself keeps, so a next occurrence stamped at 18:00 UTC is named the day it falls on
     * where its members are rather than the day it falls on at Greenwich. UTC stands in when the series row is gone - a
     * fixed fallback rather than the sweep host's own default, which would make the label depend on where the producer
     * happened to run.
     */
    private static ZoneId zoneOf(SeriesEntity series) {

        return series == null ? ZoneOffset.UTC : ZoneId.of(series.getTimeZone());
    }

    private static String label(SessionEntity next, ZoneId zone) {

        // A plain dash, not a colon: the wrap-up template already prepends "Coming up next: " to this value, and
        // "Coming up next: 29 August: Structured concurrency" reads like two labels stacked on top of each other.
        var date = NEXT_SESSION_DATE.withZone(zone).format(next.getScheduledAt());
        var title = next.getTitle();
        return title == null || title.isBlank() ? date : "%s - %s".formatted(date, title);
    }
}
