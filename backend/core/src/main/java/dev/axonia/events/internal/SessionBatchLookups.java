package dev.axonia.events.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.MeetingPlace;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Resolves everything a batch of sessions needs from a handful of other tables, once for the whole batch rather than
 * once per row (or once per section). Split out of {@link SessionMapper} so that class stays a mapper - entity to DTO -
 * while this one owns the batch-read half of the job: the standing place per series, the section type per section, and
 * the attendee count per session.
 *
 * <p>
 * {@code SessionMapper.standingPlacesOf} used to be the only method doing this; the section-type and attendance lookups
 * joined it later, following the same shape.
 */
@Component
@RequiredArgsConstructor
public class SessionBatchLookups {

    private final SectionTypeRepository sectionTypeRepository;
    private final AttendanceRepository attendanceRepository;
    private final SeriesRepository seriesRepository;

    /**
     * What a session inherits from the series it belongs to: the standing place it falls back to, and the zone its wall
     * clock is read in. The two travel together because they are looked up together - the series row is already in hand
     * for the place, and reading the zone off it costs nothing - and because widening {@link #standingPlacesOf}'s
     * return type a second time is how a batch method quietly becomes an N+1 again. {@link #NO_SERIES} is what a
     * one-off answers: no place to fall back to, and no zone but the caller's.
     */
    record Standing(MeetingPlace place, String timeZone) {

        static final Standing NO_SERIES = new Standing(MeetingPlace.NONE, null);

        static Standing of(SeriesEntity series) {

            return new Standing(MeetingPlaces.of(series), series.getTimeZone());
        }
    }

    /**
     * Everything a batch of sessions resolves once and shares, rather than once per row: the standing place per series,
     * the section type per section, and the attendee count per session. {@code SessionMapper}'s private per-row
     * overload already took four arguments, so this is gathered into one record rather than lengthening its signature
     * past PMD's limit.
     */
    @Builder
    record BatchLookups(Map<Long, Standing> standingPlaces, Map<Long, SectionTypeEntity> sectionTypes,
            Map<Long, Long> attendeeCounts) {
    }

    /** Resolves every batch-shared lookup {@code SessionMapper.toSession} needs, once for the whole batch. */
    @Transactional(readOnly = true)
    public BatchLookups lookupsOf(List<SessionEntity> entities) {

        return BatchLookups.builder().standingPlaces(standingPlacesOf(entities)).sectionTypes(sectionTypesOf(entities))
                .attendeeCounts(attendeeCountsOf(entities)).build();
    }

    /**
     * The standing place of every series the batch belongs to, keyed by series id. A lookup miss and a series that
     * simply has no place read the same way, because {@link MeetingPlace#NONE} is what both answer - which is what
     * {@link MeetingPlace#resolve} wants either way. The organization is read off the batch because every caller gets
     * its rows from a query already scoped to one: there is no read path in this module that mixes tenants.
     */
    private Map<Long, Standing> standingPlacesOf(List<SessionEntity> entities) {

        var seriesIds = entities.stream().map(SessionEntity::getSeriesId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        var organizationId = entities.getFirst().getOrganizationId();
        return seriesRepository.findByOrganizationIdAndIdIn(organizationId, seriesIds).stream()
                .collect(Collectors.toMap(SeriesEntity::getId, Standing::of));
    }

    /**
     * The section type of every section the batch's sessions carry, keyed by section type id. An empty {@code IN ()} is
     * a syntax error in Postgres, and a page of one-off sessions with no sections at all is a real case, so an empty
     * batch of type ids is answered without a query.
     */
    private Map<Long, SectionTypeEntity> sectionTypesOf(List<SessionEntity> entities) {

        var typeIds = entities.stream()
                .flatMap(e -> Objects.requireNonNullElse(e.getSections(), List.<SectionEntity>of()).stream())
                .map(SectionEntity::getSectionTypeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return sectionTypeRepository.findByOrganizationIdAndIdIn(entities.getFirst().getOrganizationId(), typeIds)
                .stream().collect(Collectors.toMap(SectionTypeEntity::getId, t -> t));
    }

    /** The attendee count of every session in the batch, keyed by session id. A miss reads as zero attendees. */
    private Map<Long, Long> attendeeCountsOf(List<SessionEntity> entities) {

        if (entities.isEmpty()) {
            return Map.of();
        }
        var sessionIds = entities.stream().map(SessionEntity::getId).collect(Collectors.toSet());
        return attendanceRepository.countBySessionIdIn(entities.getFirst().getOrganizationId(), sessionIds).stream()
                .collect(Collectors.toMap(SessionAttendeeCount::sessionId, SessionAttendeeCount::attendeeCount));
    }

    /**
     * Every section type a series' {@code defaultStructure} refers to, keyed by section type id, for
     * {@code SessionMapper.projectSections} to resolve a virtual occurrence's labels in one read rather than one per
     * ref. Same empty-collection guard as {@link #sectionTypesOf}, for the same reason: a structure with no section
     * types at all is a real case.
     */
    @Transactional(readOnly = true)
    public Map<Long, SectionTypeEntity> sectionTypesOfRefs(String organizationId, List<SectionTypeRef> refs) {

        var typeIds = refs.stream().map(SectionTypeRef::getSectionTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return sectionTypeRepository.findByOrganizationIdAndIdIn(organizationId, typeIds).stream()
                .collect(Collectors.toMap(SectionTypeEntity::getId, t -> t));
    }
}
