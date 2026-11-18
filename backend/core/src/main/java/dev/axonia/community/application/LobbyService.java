package dev.axonia.community.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityListing;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.Lobby;
import dev.axonia.community.api.LobbyItem;
import dev.axonia.community.api.LobbyLately;
import dev.axonia.community.api.LobbyShelf;
import dev.axonia.community.api.LobbyShelfKey;
import dev.axonia.community.api.LobbyStats;
import dev.axonia.community.api.spi.LibraryShowcase;
import dev.axonia.community.api.spi.LibraryShowcase.LibrarySignals;
import dev.axonia.community.api.spi.SessionShowcase;
import dev.axonia.community.api.spi.SessionShowcase.SessionSignals;
import dev.axonia.community.application.port.out.CommunityKindStore;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.LobbyCandidate;
import dev.axonia.community.domain.LobbyRanking;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.media.api.MediaLinks;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * The communities lobby. Every active community of the organization is ranked from cheap grouped reads, so a page of a
 * shelf follows the page before it; the signals the contributors carry are then fetched for the returned page only.
 */
@Service
@RequiredArgsConstructor
public class LobbyService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final int SHELF_PAGE_SIZE = 8;
    private static final int GRID_PAGE_SIZE = 20;
    private static final int FACES = 12;
    private static final Duration GROWTH_WINDOW = Duration.ofDays(30);
    private static final SessionSignals NO_SESSIONS = new SessionSignals(null, null, 0, null);
    private static final LibrarySignals NO_LIBRARY = new LibrarySignals(0, null);

    private final CommunityStore communityStore;
    private final MembershipLedger membershipLedger;
    private final CommunityKindStore communityKindStore;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final MediaLinks mediaLinks;
    private final List<SessionShowcase> sessionShowcases;
    private final List<LibraryShowcase> libraryShowcases;
    private final Clock clock;

    /** The first page a shelf loads with the lobby, and the default size of its further pages. */
    public static int pageSize(LobbyShelfKey key) {

        return switch (key) {
            case JOINED, THIS_WEEK, GROWING -> SHELF_PAGE_SIZE;
            case OTHERS, EXPLORE -> GRID_PAGE_SIZE;
        };
    }

    @Transactional(readOnly = true)
    public Lobby lobby() {

        var snapshot = snapshot();
        var ranking = snapshot.ranking();
        var firstPages = new LinkedHashMap<LobbyShelfKey, List<LobbyCandidate>>();
        ranking.shelves().forEach((key, candidates) -> firstPages.put(key,
                candidates.subList(0, Math.min(pageSize(key), candidates.size()))));
        var shown = new LinkedHashSet<>(ranking.billboard());
        firstPages.values().forEach(shown::addAll);
        var items = items(snapshot, shown);
        var shelves = firstPages.entrySet().stream().map(page -> new LobbyShelf(page.getKey(),
                itemsOf(page.getValue(), items), ranking.shelves().get(page.getKey()).size() > page.getValue().size()))
                .toList();
        return new Lobby(itemsOf(ranking.billboard(), items), shelves);
    }

    @Transactional(readOnly = true)
    public Page<LobbyItem> shelf(LobbyShelfKey key, Pageable pageable) {

        var snapshot = snapshot();
        return page(snapshot, snapshot.ranking().shelves().getOrDefault(key, List.of()), pageable);
    }

    @Transactional(readOnly = true)
    public Page<LobbyItem> search(String q, Pageable pageable) {

        var snapshot = snapshot();
        var matching = communityStore
                .searchByName(snapshot.organizationId(), CommunityStatus.ACTIVE, Objects.requireNonNullElse(q, ""),
                        Pageable.unpaged())
                .stream().map(CommunityRecord::getId).collect(Collectors.toUnmodifiableSet());
        return page(snapshot, snapshot.ranking().byName(matching), pageable);
    }

    private Snapshot snapshot() {

        var organizationId = organizationContext.organizationId();
        var now = clock.instant();
        var records = communityStore.searchByName(organizationId, CommunityStatus.ACTIVE, "", Pageable.unpaged())
                .stream().collect(Collectors.toMap(CommunityRecord::getId, Function.identity()));
        var roles = membershipLedger.findAllOf(organizationId, currentUser.userId()).stream()
                .collect(Collectors.toMap(MembershipRecord::getCommunityId, MembershipRecord::getRole));
        var members = membershipLedger.memberCounts(organizationId);
        var joins = membershipLedger.joinsWithin(organizationId, now.minus(GROWTH_WINDOW), now);
        var next = nextSessionsAt(records.keySet(), now);
        var candidates = records.values().stream()
                .map(record -> LobbyCandidate.builder().id(record.getId()).name(record.getName())
                        .memberCount(members.getOrDefault(record.getId(), 0L)).joined(roles.containsKey(record.getId()))
                        .joinedLast30Days(joins.getOrDefault(record.getId(), 0L))
                        .nextSessionAt(next.get(record.getId())).build())
                .toList();
        return Snapshot.builder().organizationId(organizationId).now(now).records(records).roles(roles)
                .ranking(LobbyRanking.of(candidates, now)).build();
    }

    private Map<Long, Instant> nextSessionsAt(Set<Long> ids, Instant now) {

        var next = new HashMap<Long, Instant>();
        if (!ids.isEmpty()) {
            sessionShowcases.forEach(showcase -> next.putAll(showcase.nextSessionsAt(ids, now)));
        }
        return next;
    }

    private Page<LobbyItem> page(Snapshot snapshot, List<LobbyCandidate> candidates, Pageable pageable) {

        var from = (int) Math.min(pageable.getOffset(), candidates.size());
        var to = Math.min(from + pageable.getPageSize(), candidates.size());
        var slice = candidates.subList(from, to);
        return new PageImpl<>(itemsOf(slice, items(snapshot, slice)), pageable, candidates.size());
    }

    private Map<Long, LobbyItem> items(Snapshot snapshot, Collection<LobbyCandidate> candidates) {

        if (candidates.isEmpty()) {
            return Map.of();
        }
        var organizationId = snapshot.organizationId();
        var ids = candidates.stream().map(LobbyCandidate::id).collect(Collectors.toUnmodifiableSet());
        var now = snapshot.now();
        var faces = membershipLedger.newestMemberIds(organizationId, ids, FACES);
        var sessions = new HashMap<Long, SessionSignals>();
        sessionShowcases.forEach(showcase -> sessions.putAll(showcase.signals(ids, now)));
        var library = new HashMap<Long, LibrarySignals>();
        libraryShowcases.forEach(showcase -> library.putAll(showcase.signals(ids)));
        var labels = CommunityListings.kindLabels(communityKindStore, organizationId);
        var items = new HashMap<Long, LobbyItem>();
        for (var candidate : candidates) {
            var record = snapshot.records().get(candidate.id());
            var session = sessions.getOrDefault(candidate.id(), NO_SESSIONS);
            var shared = library.getOrDefault(candidate.id(), NO_LIBRARY);
            var community = CommunityListings.toCommunity(record, candidate.memberCount(),
                    labels.get(record.getKindId()), mediaLinks);
            items.put(candidate.id(),
                    LobbyItem.builder().listing(new CommunityListing(community, snapshot.roles().get(candidate.id())))
                            .nextSession(session.next()).nextSessionThisWeek(snapshot.ranking().thisWeek(candidate))
                            .latestSession(session.latest()).joinedLast30Days(candidate.joinedLast30Days())
                            .faces(faces.getOrDefault(candidate.id(), List.of()))
                            .stats(new LobbyStats(session.held(), shared.shared()))
                            .lately(new LobbyLately(shared.latestTitle(), session.latestTopicTitle())).build());
        }
        return items;
    }

    private static List<LobbyItem> itemsOf(List<LobbyCandidate> candidates, Map<Long, LobbyItem> items) {

        return candidates.stream().map(candidate -> items.get(candidate.id())).toList();
    }

    @Builder
    private record Snapshot(String organizationId, Instant now, Map<Long, CommunityRecord> records,
            Map<Long, CommunityRole> roles, LobbyRanking ranking) {
    }
}
