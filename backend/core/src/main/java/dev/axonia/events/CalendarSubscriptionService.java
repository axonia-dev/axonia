package dev.axonia.events;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.OrganizationScope;
import dev.axonia.PublicUrlProperties;
import dev.axonia.events.internal.CalendarSubscriptionEntity;
import dev.axonia.events.internal.CalendarSubscriptionRepository;
import dev.axonia.events.internal.IcsDocumentRequest;
import dev.axonia.events.internal.ScheduleIcsDocument;
import dev.axonia.events.internal.SessionUrls;
import dev.axonia.events.internal.SubscriptionTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A member's per-community calendar subscription, and the document it serves.
 *
 * <p>
 * The token in the feed URL is the credential, so everything a calendar client asks for is resolved from it and nothing
 * else. {@link #feed} therefore refuses an unknown token, a subscription whose owner has left the community, and one
 * whose Organization is no longer {@code ACTIVE} with the same {@link NotFoundException} and the same message: a
 * forged, a dead and a revoked link must be indistinguishable from outside.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSubscriptionService {

    /** 30 days back to 180 days forward, computed per fetch: a rolling window. */
    private static final int WINDOW_BACK_DAYS = 30;
    private static final int WINDOW_FORWARD_DAYS = 180;

    /** The width of {@code last_fetched_agent}. A client sending something longer is stored trimmed, not refused. */
    private static final int AGENT_LIMIT = 255;

    private static final String NOT_FOUND = "Calendar not found";

    private final CalendarSubscriptionRepository repository;
    private final CommunityAccess communityAccess;
    private final OrganizationAccess organizationAccess;
    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final ScheduleService scheduleService;
    private final PublicUrlProperties publicUrlProperties;
    private final Clock clock;

    /**
     * Find-or-create. An existing subscription comes back without a URL: the token was never stored, so it cannot be
     * shown twice, and a member who has lost theirs resets rather than subscribes again.
     */
    @Transactional
    public CalendarSubscription subscribe(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var userId = currentUser.userId();
        if (!communityAccess.isMember(communityId, userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var existing = repository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId);
        if (existing.isPresent()) {
            return asModel(existing.get(), null);
        }
        var token = SubscriptionTokens.mint();
        var saved = repository.save(CalendarSubscriptionEntity.builder().organizationId(organizationId)
                .communityId(communityId).userId(userId).tokenHash(SubscriptionTokens.hash(token)).build());
        return asModel(saved, feedUrl(token));
    }

    /**
     * Overwrites the hash in place, which is what makes a reset actually invalidate: every calendar still polling the
     * old URL stops resolving. The fetch stamp is cleared with it, because it describes a subscription that no longer
     * exists.
     */
    @Transactional
    public CalendarSubscription reset(String slug) {

        var existing = mine(slug).orElseThrow(CalendarSubscriptionService::notFound);
        var token = SubscriptionTokens.mint();
        var saved = repository.save(existing.toBuilder().tokenHash(SubscriptionTokens.hash(token)).lastFetchedAt(null)
                .lastFetchedAgent(null).build());
        return asModel(saved, feedUrl(token));
    }

    @Transactional(readOnly = true)
    public Optional<CalendarSubscription> current(String slug) {

        return mine(slug).map(entity -> asModel(entity, null));
    }

    /**
     * The token lookup runs before any Organization is in scope, which is what
     * {@code calendar_subscription_auth_lookup} exists for, and everything the tenant's own policies cover runs inside
     * {@code OrganizationScope.runAs}. The scope wraps the read rather than sitting inside it, because
     * {@code app.current_org} is chosen when a transaction acquires its connection.
     */
    public CalendarFeed feed(String token, Instant now) {

        var subscription = repository.findByTokenHash(SubscriptionTokens.hash(token))
                .orElseThrow(CalendarSubscriptionService::notFound);
        return OrganizationScope.runAs(subscription.getOrganizationId(), () -> publish(subscription, now));
    }

    /**
     * Records that a calendar actually pulled the feed, which is the only evidence a member ever gets that the URL
     * pasted into their client works.
     *
     * <p>
     * Its own read and its own write, outside the fetch it reports on, and it swallows every failure of both. The stamp
     * is bookkeeping about a fetch rather than part of one: two calendar clients polling the same URL collide on the
     * row's version sooner or later, and turning that lost race into a 500 would break the subscription this method
     * exists to reassure the member about. Separating the transaction is not enough on its own - the exception still
     * has to stop here.
     */
    public void recordFetch(String token, String userAgent) {

        try {
            repository.findByTokenHash(SubscriptionTokens.hash(token))
                    .ifPresent(subscription -> OrganizationScope.runAs(subscription.getOrganizationId(),
                            () -> repository.stampFetch(subscription.getId(), clock.instant(), trimmed(userAgent))));
        } catch (RuntimeException e) {
            // Never the token, not even hashed: this line reaches the log of an unauthenticated, unattended poll.
            log.warn("Could not record a calendar fetch", e);
        }
    }

    /**
     * {@code now} decides the window and nothing else. The document's DTSTAMP comes from
     * {@link ScheduleIcsDocument#stampFor}, so a poll that finds nothing changed renders the same bytes, hashes to the
     * same {@code ETag} and is answered with a bodiless 304 rather than the whole schedule.
     */
    private CalendarFeed publish(CalendarSubscriptionEntity subscription, Instant now) {

        var organizationId = subscription.getOrganizationId();
        var communityId = subscription.getCommunityId();
        if (!organizationAccess.isActive(organizationId)
                || !communityAccess.isMember(organizationId, communityId, subscription.getUserId())) {
            throw notFound();
        }
        var identity = communityAccess.communityIdentity(organizationId, communityId);
        var entries = scheduleService.schedule(ScheduleQuery.builder().organizationId(organizationId)
                .communityId(communityId).from(now.minus(WINDOW_BACK_DAYS, ChronoUnit.DAYS))
                .to(now.plus(WINDOW_FORWARD_DAYS, ChronoUnit.DAYS)).now(now).build());
        var stamp = ScheduleIcsDocument.stampFor(entries, now);
        var request = IcsDocumentRequest.builder().calendarName(identity.name()).host(feedHost())
                .sessionBaseUrl(SessionUrls.sessionBaseUrl(publicUrlProperties.publicBaseUrl(), identity.slug()))
                .stamp(stamp).entries(entries).build();
        return new CalendarFeed(identity.name(), ScheduleIcsDocument.render(request));
    }

    private Optional<CalendarSubscriptionEntity> mine(String slug) {

        return repository.findByOrganizationIdAndCommunityIdAndUserId(organizationContext.organizationId(),
                communityAccess.communityIdForSlug(slug), currentUser.userId());
    }

    private CalendarSubscription asModel(CalendarSubscriptionEntity entity, String url) {

        return new CalendarSubscription(url, entity.getLastFetchedAt(), entity.getLastFetchedAgent());
    }

    private String feedUrl(String token) {

        return publicUrlProperties.publicBaseUrl() + "/public/calendar/" + token + ".ics";
    }

    /** The domain half of every UID, so an occurrence keeps one identity across every calendar that holds it. */
    private String feedHost() {

        return URI.create(publicUrlProperties.publicBaseUrl()).getHost();
    }

    private String trimmed(String userAgent) {

        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() <= AGENT_LIMIT ? userAgent : userAgent.substring(0, AGENT_LIMIT);
    }

    private static NotFoundException notFound() {

        return new NotFoundException(NOT_FOUND);
    }
}
