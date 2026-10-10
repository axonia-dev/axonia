package dev.axonia.events.application.port.out;

import java.time.Instant;
import java.util.Optional;

import dev.axonia.events.domain.CalendarSubscriptionRecord;

public interface CalendarSubscriptionStore {

    /** The one read that runs before any Organization is in scope. */
    Optional<CalendarSubscriptionRecord> findByTokenHash(String tokenHash);

    Optional<CalendarSubscriptionRecord> findByOrganizationIdAndCommunityIdAndUserId(String organizationId,
            Long communityId, String userId);

    void stampFetch(Long id, Instant at, String agent);

    CalendarSubscriptionRecord save(CalendarSubscriptionRecord subscription);
}
