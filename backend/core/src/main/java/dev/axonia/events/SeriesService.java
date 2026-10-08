package dev.axonia.events;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.events.internal.MeetingPlaces;
import dev.axonia.events.internal.NextSlot;
import dev.axonia.events.internal.NextSlotQuery;
import dev.axonia.events.internal.SectionTypeCatalog;
import dev.axonia.events.internal.SectionTypeRef;
import dev.axonia.events.internal.SeriesEntity;
import dev.axonia.events.internal.SeriesRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final SectionTypeCatalog sectionTypeCatalog;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;

    @Transactional
    public Series start(String slug, CreateSeriesRequest request) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        var userId = currentUser.userId();
        if (!communityAccess.isOrganizer(communityId, userId)
                && !memberMay(communityId, userId, communityAccess.contributionPolicy(communityId).canStartSeries())) {
            throw new ForbiddenException("Only an organizer may start a series");
        }

        var structure = toRefs(organizationId, request.defaultStructure());
        var draft = SeriesEntity.builder().organizationId(organizationId).communityId(communityId)
                .title(request.title()).cadenceCount(request.cadence().count()).cadenceUnit(request.cadence().unit())
                .startsOn(request.startsOn()).preferredTime(request.preferredTime()).timeZone(request.timeZone())
                .durationMinutes(request.durationMinutes()).status(SeriesStatus.ACTIVE).defaultStructure(structure);

        return toSeries(seriesRepository.save(placed(draft, request.place()).build()));
    }

    @Transactional(readOnly = true)
    public Series get(Long id) {

        return toSeries(findOrThrow(organizationContext.organizationId(), id));
    }

    @Transactional(readOnly = true)
    public Page<Series> list(String slug, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return seriesRepository.findByOrganizationIdAndCommunityId(organizationId, communityId, pageable)
                .map(this::toSeries);
    }

    @Transactional
    public Series update(Long id, UpdateSeriesRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = requireEditableBy(id);
        var structure = toRefs(organizationId, request.defaultStructure());
        var draft = entity.toBuilder().title(request.title()).cadenceCount(request.cadence().count())
                .cadenceUnit(request.cadence().unit()).startsOn(request.startsOn())
                .preferredTime(request.preferredTime()).timeZone(request.timeZone())
                .durationMinutes(request.durationMinutes()).defaultStructure(structure);

        return toSeries(seriesRepository.save(placed(draft, request.place()).build()));
    }

    /**
     * The standing place, written as the three columns it is stored in. Both write paths go through here so the parts
     * cannot be spread in a different order on one of them, and so an absent place is stored as three nulls rather than
     * reaching the builder as one.
     */
    private static SeriesEntity.SeriesEntityBuilder placed(SeriesEntity.SeriesEntityBuilder draft, MeetingPlace place) {

        var stated = place == null ? MeetingPlace.NONE : place;
        return draft.location(stated.location()).joinUrl(stated.joinUrl()).joiningDetails(stated.joiningDetails());
    }

    @Transactional
    public Series pause(Long id) {

        return transition(id, SeriesStatus.PAUSED);
    }

    @Transactional
    public Series resume(Long id) {

        return transition(id, SeriesStatus.ACTIVE);
    }

    @Transactional
    public Series end(Long id) {

        return transition(id, SeriesStatus.ENDED);
    }

    private Series transition(Long id, SeriesStatus status) {

        var entity = requireOrganizerOn(id);
        return toSeries(seriesRepository.save(entity.toBuilder().status(status).build()));
    }

    private List<SectionTypeRef> toRefs(String organizationId, List<SectionTemplate> templates) {

        return templates.stream().map(t -> {
            sectionTypeCatalog.require(organizationId, t.sectionTypeId());
            return SectionTypeRef.builder().sectionTypeId(t.sectionTypeId()).title(t.title())
                    .durationMinutes(t.durationMinutes()).build();
        }).toList();
    }

    private boolean memberMay(long communityId, String userId, boolean axisOpen) {

        return axisOpen && communityAccess.isMember(communityId, userId);
    }

    private SeriesEntity requireEditableBy(Long id) {

        var entity = findOrThrow(organizationContext.organizationId(), id);
        var userId = currentUser.userId();
        var communityId = entity.getCommunityId();
        if (!communityAccess.isOrganizer(communityId, userId) && !memberMay(communityId, userId,
                communityAccess.contributionPolicy(communityId).canEditStructure())) {
            throw new ForbiddenException("Organizer role required");
        }
        return entity;
    }

    private SeriesEntity requireOrganizerOn(Long id) {

        var entity = findOrThrow(organizationContext.organizationId(), id);
        if (!communityAccess.isOrganizer(entity.getCommunityId(), currentUser.userId())) {
            throw new ForbiddenException("Organizer role required");
        }
        return entity;
    }

    SeriesEntity findOrThrow(String organizationId, Long id) {

        return seriesRepository.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Series not found: " + id));
    }

    Series toSeries(SeriesEntity e) {

        var cadence = new Cadence(e.getCadenceCount(), e.getCadenceUnit());
        var zone = ZoneId.of(e.getTimeZone());
        var next = e.getStatus() == SeriesStatus.ACTIVE
                ? NextSlot.suggest(NextSlotQuery.builder().cadence(cadence).startsOn(e.getStartsOn())
                        .preferredTime(e.getPreferredTime()).zone(zone).now(Instant.now()).build()).orElse(null)
                : null;
        var structure = e.getDefaultStructure().stream()
                .map(r -> new SectionTemplate(r.getSectionTypeId(), r.getTitle(), r.getDurationMinutes())).toList();
        return Series.builder().id(e.getId()).communityId(e.getCommunityId()).title(e.getTitle()).cadence(cadence)
                .startsOn(e.getStartsOn()).preferredTime(e.getPreferredTime()).timeZone(e.getTimeZone())
                .place(MeetingPlaces.of(e)).durationMinutes(e.getDurationMinutes()).status(e.getStatus())
                .defaultStructure(structure).nextSuggestedAt(next).createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt()).build();
    }
}
