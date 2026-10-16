package dev.axonia.events.application;

import java.time.Clock;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Memory;
import dev.axonia.events.api.MemoryCursor;
import dev.axonia.events.api.MemoryPage;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionListing;
import lombok.RequiredArgsConstructor;

/** Past sessions newest first with their traces; a skipped one is no memory but still advances the keyset cursor. */
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final SessionListing sessionListing;
    private final CommunityAccess communityAccess;
    private final OrganizationContext organizationContext;
    private final MemoryTraceAssembler memoryTraceAssembler;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MemoryPage memories(String slug, MemoryCursor before, int size) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var query = SessionPageQuery.builder().organizationId(organizationId).communityId(communityId)
                .window(SessionPageQuery.Window.PAST).now(clock.instant()).pageable(PageRequest.of(0, size));
        if (before != null) {
            query.to(before.at()).beforeId(before.id());
        }
        var fetched = sessionListing.page(query.build());
        var kept = fetched.stream().filter(session -> session.status() != SessionStatus.SKIPPED).toList();
        var traces = memoryTraceAssembler.tracesFor(organizationId, communityId, kept);
        var items = kept.stream().map(session -> new Memory(ScheduleEntries.of(session), traces.get(session.id())))
                .toList();
        return new MemoryPage(items, fetched.size() < size ? null : cursorOf(fetched.getLast()));
    }

    private static MemoryCursor cursorOf(Session session) {

        return new MemoryCursor(session.scheduledAt(), session.id());
    }
}
