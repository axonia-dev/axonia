package dev.axonia.events.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.domain.MeetingPlaces;
import dev.axonia.events.domain.PlannedSection;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SectionTypeRecord;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SessionAttendeeCount;
import dev.axonia.events.domain.SessionRecord;
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

    private final SectionTypeStore sectionTypeStore;
    private final AttendanceStore attendanceStore;
    private final SeriesStore seriesStore;

    /**
     * What a session inherits from the series it belongs to: the standing place it falls back to, and the zone its wall
     * clock is read in. The two travel together because they are looked up together - the series row is already in hand
     * for the place, and reading the zone off it costs nothing - and because widening {@link #standingPlacesOf}'s
     * return type a second time is how a batch method quietly becomes an N+1 again. {@link #NO_SERIES} is what a
     * one-off answers: no place to fall back to, and no zone but the caller's.
     */
    public record Standing(MeetingPlace place, String timeZone) {

        public static final Standing NO_SERIES = new Standing(MeetingPlace.NONE, null);

        static Standing of(SeriesRecord series) {

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
    public record BatchLookups(Map<Long, Standing> standingPlaces, Map<Long, SectionTypeRecord> sectionTypes,
            Map<Long, Long> attendeeCounts) {
    }

    /** Resolves every batch-shared lookup {@code SessionMapper.toSession} needs, once for the whole batch. */
    @Transactional(readOnly = true)
    public BatchLookups lookupsOf(List<SessionRecord> entities) {

        return BatchLookups.builder().standingPlaces(standingPlacesOf(entities)).sectionTypes(sectionTypesOf(entities))
                .attendeeCounts(attendeeCountsOf(entities)).build();
    }

    /**
     * The standing place of every series the batch belongs to, keyed by series id. A lookup miss and a series that
     * simply has no place read the same way, because {@link MeetingPlace#NONE} is what both answer - which is what
     * {@link MeetingPlace#resolve} wants either way. The organization is read off the batch because every caller gets
     * its rows from a query already scoped to one: there is no read path in this module that mixes tenants.
     */
    private Map<Long, Standing> standingPlacesOf(List<SessionRecord> entities) {

        var seriesIds = entities.stream().map(SessionRecord::getSeriesId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        var organizationId = entities.getFirst().getOrganizationId();
        return seriesStore.findByOrganizationIdAndIdIn(organizationId, seriesIds).stream()
                .collect(Collectors.toMap(SeriesRecord::getId, Standing::of));
    }

    /**
     * The section type of every section the batch's sessions carry, keyed by section type id. An empty {@code IN ()} is
     * a syntax error in Postgres, and a page of one-off sessions with no sections at all is a real case, so an empty
     * batch of type ids is answered without a query.
     */
    private Map<Long, SectionTypeRecord> sectionTypesOf(List<SessionRecord> entities) {

        var typeIds = entities.stream()
                .flatMap(e -> Objects.requireNonNullElse(e.getSections(), List.<SectionRecord>of()).stream())
                .map(SectionRecord::getSectionTypeId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return sectionTypeStore.findByOrganizationIdAndIdIn(entities.getFirst().getOrganizationId(), typeIds).stream()
                .collect(Collectors.toMap(SectionTypeRecord::getId, t -> t));
    }

    /** The attendee count of every session in the batch, keyed by session id. A miss reads as zero attendees. */
    private Map<Long, Long> attendeeCountsOf(List<SessionRecord> entities) {

        if (entities.isEmpty()) {
            return Map.of();
        }
        var sessionIds = entities.stream().map(SessionRecord::getId).collect(Collectors.toSet());
        return attendanceStore.countBySessionIdIn(entities.getFirst().getOrganizationId(), sessionIds).stream()
                .collect(Collectors.toMap(SessionAttendeeCount::sessionId, SessionAttendeeCount::attendeeCount));
    }

    /**
     * Every section type a series' {@code defaultStructure} refers to, keyed by section type id, for
     * {@code SessionMapper.projectSections} to resolve a virtual occurrence's labels in one read rather than one per
     * ref. Same empty-collection guard as {@link #sectionTypesOf}, for the same reason: a structure with no section
     * types at all is a real case.
     */
    @Transactional(readOnly = true)
    public Map<Long, SectionTypeRecord> sectionTypesOfRefs(String organizationId, List<PlannedSection> refs) {

        var typeIds = refs.stream().map(PlannedSection::getSectionTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        return sectionTypeStore.findByOrganizationIdAndIdIn(organizationId, typeIds).stream()
                .collect(Collectors.toMap(SectionTypeRecord::getId, t -> t));
    }
}
