package dev.axonia.events.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Ribbon;
import dev.axonia.events.api.RibbonTick;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The schedule's time scrubber: the window's past sessions as memories, and from now on what the schedule plans or a
 * series projects. The past is read from the sessions alone, because a projection of the past is no memory.
 *
 * <p>
 * The schedule is asked for the whole window, not from now: an occurrence moved from a future anchor to a past instant
 * only suppresses its anchor's projection when its real row is read, and anything before now is dropped afterwards.
 */
@Service
@RequiredArgsConstructor
public class ScheduleRibbonService {

    private static final Duration MAX_SPAN = Duration.ofDays(1500);
    private static final Set<SessionStatus> DROPPED = EnumSet.of(SessionStatus.CANCELLED, SessionStatus.SKIPPED);

    private final SessionListing sessionListing;
    private final ScheduleService scheduleService;
    private final CommunityAccess communityAccess;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Ribbon ribbon(String slug, Instant from, Instant to) {

        if (!from.isBefore(to)) {
            throw new ValidationException("RIBBON_WINDOW_EMPTY", "The ribbon window must end after it starts");
        }
        if (Duration.between(from, to).compareTo(MAX_SPAN) > 0) {
            throw new ValidationException("RIBBON_WINDOW_TOO_WIDE", "The ribbon window spans at most 1500 days");
        }
        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var now = clock.instant();
        var ticks = new ArrayList<RibbonTick>();
        if (from.isBefore(now)) {
            sessionListing.instants(organizationId, communityId, from, earlier(now, to))
                    .forEach(at -> ticks.add(new RibbonTick(at, RibbonTick.Kind.MEMORY)));
        }
        if (to.isAfter(now)) {
            ticks.addAll(ahead(ScheduleQuery.builder().organizationId(organizationId).communityId(communityId)
                    .from(from).to(to).now(now).build()));
        }
        ticks.sort(Comparator.comparing(RibbonTick::at));
        return new Ribbon(sessionListing.firstSessionAt(organizationId, communityId).orElse(null), ticks);
    }

    private List<RibbonTick> ahead(ScheduleQuery query) {

        return scheduleService.schedule(query).stream().filter(entry -> isAhead(entry, query.now()))
                .map(entry -> new RibbonTick(entry.scheduledAt(),
                        entry.virtual() ? RibbonTick.Kind.PROJECTED : RibbonTick.Kind.UPCOMING))
                .toList();
    }

    private static boolean isAhead(ScheduleEntry entry, Instant now) {

        return entry.scheduledAt() != null && !entry.scheduledAt().isBefore(now) && !DROPPED.contains(entry.status());
    }

    private static Instant earlier(Instant a, Instant b) {

        return a.isBefore(b) ? a : b;
    }
}
