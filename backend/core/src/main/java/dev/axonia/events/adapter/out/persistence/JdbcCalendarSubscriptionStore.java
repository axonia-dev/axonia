package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.CalendarSubscriptionStore;
import dev.axonia.events.domain.CalendarSubscriptionRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcCalendarSubscriptionStore implements CalendarSubscriptionStore {

    private final CalendarSubscriptionRepository calendarSubscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CalendarSubscriptionRecord> findByTokenHash(String tokenHash) {

        return calendarSubscriptionRepository.findByTokenHash(tokenHash).map(JdbcCalendarSubscriptionStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CalendarSubscriptionRecord> findByOrganizationIdAndCommunityIdAndUserId(String organizationId,
            Long communityId, String userId) {

        return calendarSubscriptionRepository
                .findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .map(JdbcCalendarSubscriptionStore::toRecord);
    }

    @Override
    public void stampFetch(Long id, Instant at, String agent) {

        calendarSubscriptionRepository.stampFetch(id, at, agent);
    }

    @Override
    @Transactional
    public CalendarSubscriptionRecord save(CalendarSubscriptionRecord subscription) {

        return toRecord(calendarSubscriptionRepository.save(toEntity(subscription)));
    }

    static CalendarSubscriptionRecord toRecord(CalendarSubscriptionEntity entity) {

        return CalendarSubscriptionRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).userId(entity.getUserId()).tokenHash(entity.getTokenHash())
                .createdAt(entity.getCreatedAt()).lastFetchedAt(entity.getLastFetchedAt())
                .lastFetchedAgent(entity.getLastFetchedAgent()).version(entity.getVersion()).build();
    }

    static CalendarSubscriptionEntity toEntity(CalendarSubscriptionRecord subscription) {

        return CalendarSubscriptionEntity.builder().id(subscription.getId())
                .organizationId(subscription.getOrganizationId()).communityId(subscription.getCommunityId())
                .userId(subscription.getUserId()).tokenHash(subscription.getTokenHash())
                .createdAt(subscription.getCreatedAt()).lastFetchedAt(subscription.getLastFetchedAt())
                .lastFetchedAgent(subscription.getLastFetchedAgent()).version(subscription.getVersion()).build();
    }
}
