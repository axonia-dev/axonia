package dev.axonia.events.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.events.application.PastSessionsQuery;
import dev.axonia.events.domain.SessionRecord;

public interface SessionStore {

    Optional<SessionRecord> findByOrganizationIdAndId(String organizationId, Long id);

    Optional<SessionRecord> findByOrganizationIdAndSeriesIdAndOccurrenceDate(String organizationId, Long seriesId,
            LocalDate occurrenceDate);

    List<SessionRecord> findByOrganizationIdAndCommunityIdAndIdIn(String organizationId, Long communityId,
            Collection<Long> ids);

    Optional<SessionRecord> findPendingForSeriesExcluding(String organizationId, Long seriesId, Long excludeSessionId,
            Instant now);

    Optional<SessionRecord> findSoonestUpcoming(String organizationId, Long communityId, Instant now);

    long countUpcoming(String organizationId, Long communityId, Instant now);

    long countPast(PastSessionsQuery query);

    List<String> findActiveMonths(String organizationId, Long communityId);

    List<Long> findUpcomingNoticeSessionIds(String organizationId, Instant now, Instant since);

    List<Long> findWrapUpSessionIds(String organizationId, Instant now, Instant since);

    List<Long> findOverdueUnclosedSessionIds(String organizationId, int offsetDays, Instant since, Instant now);

    SessionRecord save(SessionRecord session);
}
