package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.PastSessionsQuery;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.SlotTag;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcSessionStore implements SessionStore {

    private final SessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRecord> findByOrganizationIdAndId(String organizationId, Long id) {

        return sessionRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcSessionStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRecord> findByOrganizationIdAndSeriesIdAndOccurrenceDate(String organizationId,
            Long seriesId, LocalDate occurrenceDate) {

        return sessionRepository
                .findByOrganizationIdAndSeriesIdAndOccurrenceDate(organizationId, seriesId, occurrenceDate)
                .map(JdbcSessionStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRecord> findByOrganizationIdAndCommunityIdAndIdIn(String organizationId, Long communityId,
            Collection<Long> ids) {

        return sessionRepository.findByOrganizationIdAndCommunityIdAndIdIn(organizationId, communityId, ids).stream()
                .map(JdbcSessionStore::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRecord> findPendingForSeriesExcluding(String organizationId, Long seriesId,
            Long excludeSessionId, Instant now) {

        return sessionRepository.findPendingForSeriesExcluding(organizationId, seriesId, excludeSessionId, now)
                .map(JdbcSessionStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRecord> findSoonestUpcoming(String organizationId, Long communityId, Instant now) {

        return sessionRepository.findSoonestUpcoming(organizationId, communityId, now).map(JdbcSessionStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUpcoming(String organizationId, Long communityId, Instant now) {

        return sessionRepository.countUpcoming(organizationId, communityId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPast(PastSessionsQuery query) {

        return sessionRepository.countPast(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findActiveMonths(String organizationId, Long communityId) {

        return sessionRepository.findActiveMonths(organizationId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findUpcomingNoticeSessionIds(String organizationId, Instant now, Instant since) {

        return sessionRepository.findUpcomingNoticeSessionIds(organizationId, now, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findWrapUpSessionIds(String organizationId, Instant now, Instant since) {

        return sessionRepository.findWrapUpSessionIds(organizationId, now, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverdueUnclosedSessionIds(String organizationId, int offsetDays, Instant since, Instant now) {

        return sessionRepository.findOverdueUnclosedSessionIds(organizationId, offsetDays, since, now);
    }

    @Override
    @Transactional
    public SessionRecord save(SessionRecord session) {

        return toRecord(sessionRepository.save(toEntity(session)));
    }

    static SessionRecord toRecord(SessionEntity entity) {

        return SessionRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .seriesId(entity.getSeriesId()).communityId(entity.getCommunityId()).status(entity.getStatus())
                .scheduledAt(entity.getScheduledAt()).hasTime(entity.isHasTime()).timeZone(entity.getTimeZone())
                .occurrenceDate(entity.getOccurrenceDate()).title(entity.getTitle())
                .durationMinutes(entity.getDurationMinutes()).facilitatorUserId(entity.getFacilitatorUserId())
                .notes(entity.getNotes()).recordingUrl(entity.getRecordingUrl()).location(entity.getLocation())
                .joinUrl(entity.getJoinUrl()).joiningDetails(entity.getJoiningDetails()).closedAt(entity.getClosedAt())
                .wrapUpReleaseAt(entity.getWrapUpReleaseAt()).upcomingReleaseAt(entity.getUpcomingReleaseAt())
                .sections(RecordMapping.list(entity.getSections(), JdbcSessionStore::toSectionRecord))
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static SessionEntity toEntity(SessionRecord session) {

        return SessionEntity.builder().id(session.getId()).organizationId(session.getOrganizationId())
                .seriesId(session.getSeriesId()).communityId(session.getCommunityId()).status(session.getStatus())
                .scheduledAt(session.getScheduledAt()).hasTime(session.isHasTime()).timeZone(session.getTimeZone())
                .occurrenceDate(session.getOccurrenceDate()).title(session.getTitle())
                .durationMinutes(session.getDurationMinutes()).facilitatorUserId(session.getFacilitatorUserId())
                .notes(session.getNotes()).recordingUrl(session.getRecordingUrl()).location(session.getLocation())
                .joinUrl(session.getJoinUrl()).joiningDetails(session.getJoiningDetails())
                .closedAt(session.getClosedAt()).wrapUpReleaseAt(session.getWrapUpReleaseAt())
                .upcomingReleaseAt(session.getUpcomingReleaseAt())
                .sections(RecordMapping.list(session.getSections(), JdbcSessionStore::toSectionEntity))
                .createdAt(session.getCreatedAt()).updatedAt(session.getUpdatedAt()).version(session.getVersion())
                .build();
    }

    static SectionRecord toSectionRecord(SectionEntity entity) {

        return SectionRecord.builder().id(entity.getId()).sectionTypeId(entity.getSectionTypeId())
                .title(entity.getTitle()).durationMinutes(entity.getDurationMinutes()).notes(entity.getNotes())
                .slots(RecordMapping.list(entity.getSlots(), JdbcSessionStore::toSlotRecord)).build();
    }

    static SectionEntity toSectionEntity(SectionRecord section) {

        return SectionEntity.builder().id(section.getId()).sectionTypeId(section.getSectionTypeId())
                .title(section.getTitle()).durationMinutes(section.getDurationMinutes()).notes(section.getNotes())
                .slots(RecordMapping.list(section.getSlots(), JdbcSessionStore::toSlotEntity)).build();
    }

    static SlotRecord toSlotRecord(SlotEntity entity) {

        return SlotRecord.builder().id(entity.getId()).topicId(entity.getTopicId()).title(entity.getTitle())
                .notes(entity.getNotes()).durationMinutes(entity.getDurationMinutes())
                .presenters(RecordMapping.set(entity.getPresenters(),
                        p -> SlotPresenter.builder().userId(p.getUserId()).build()))
                .tags(RecordMapping.set(entity.getTags(), t -> SlotTag.builder().tag(t.getTag()).build())).build();
    }

    static SlotEntity toSlotEntity(SlotRecord slot) {

        return SlotEntity.builder().id(slot.getId()).topicId(slot.getTopicId()).title(slot.getTitle())
                .notes(slot.getNotes()).durationMinutes(slot.getDurationMinutes())
                .presenters(RecordMapping.set(slot.getPresenters(),
                        p -> SlotPresenterRef.builder().userId(p.getUserId()).build()))
                .tags(RecordMapping.set(slot.getTags(), t -> SlotTagRef.builder().tag(t.getTag()).build())).build();
    }
}
