package dev.axonia.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.Schedules;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.Cadence;
import dev.axonia.events.domain.MeetingPlaces;
import dev.axonia.events.domain.NextSlot;
import dev.axonia.events.domain.OccurrenceInstant;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SeriesStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService implements Schedules {

    private final SessionStore sessionStore;
    private final SeriesStore seriesStore;
    private final SessionListing sessionListing;
    private final SessionMapper sessionMapper;
    private final CommunityAccess communityAccess;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleEntry> schedule(String slug, Instant from, Instant to) {

        return schedule(slug, from, to, Instant.now());
    }

    List<ScheduleEntry> schedule(String slug, Instant from, Instant to, Instant now) {

        return schedule(ScheduleQuery.builder().organizationId(organizationContext.organizationId())
                .communityId(communityAccess.communityIdForSlug(slug)).from(from).to(to).now(now).build());
    }

    /**
     * Org-explicit variant, for a caller with no JWT to read an organization context from: the calendar feed is
     * authorized by a token rather than by a sign-in and runs inside {@code OrganizationScope.runAs}, exactly as the
     * communication sweep's producers do for {@link CommunityAccess}'s own org-explicit overloads.
     */
    @Transactional(readOnly = true)
    public List<ScheduleEntry> schedule(ScheduleQuery query) {

        var organizationId = query.organizationId();
        var communityId = query.communityId();
        var from = query.from();
        var to = query.to();
        var now = query.now();

        // One query per level rather than one per row: see SessionListing for why the aggregate cannot be read
        // in constant cost. The month view is a list projection like any other, so it reads the same way.
        var real = sessionListing.page(SessionPageQuery.builder().organizationId(organizationId)
                .communityId(communityId).from(from).to(to).window(SessionPageQuery.Window.BETWEEN).build());

        var realEntries = real.stream().map(this::toEntry).toList();

        // Suppression set: any (seriesId, occurrenceDate) pair with a real row prevents a ghost
        var suppressed = real.stream().filter(s -> s.seriesId() != null && s.occurrenceDate() != null)
                .map(s -> s.seriesId() + ":" + s.occurrenceDate()).collect(Collectors.toSet());

        var today = OffsetDateTime.ofInstant(now, ZoneOffset.UTC).toLocalDate();
        var fromDate = OffsetDateTime.ofInstant(from, ZoneOffset.UTC).toLocalDate();
        if (fromDate.isBefore(today)) {
            fromDate = today;
        }
        var toDate = OffsetDateTime.ofInstant(to, ZoneOffset.UTC).toLocalDate();

        var activeSeries = seriesStore.findByOrganizationIdAndCommunityId(organizationId, communityId).stream()
                .filter(s -> s.getStatus() == SeriesStatus.ACTIVE).toList();

        var ghosts = new ArrayList<ScheduleEntry>();
        for (var series : activeSeries) {
            var cadence = new Cadence(series.getCadenceCount(), series.getCadenceUnit());
            var anchors = NextSlot.occurrencesBetween(cadence, series.getStartsOn(), fromDate, toDate);
            for (var anchor : anchors) {
                if (!suppressed.contains(series.getId() + ":" + anchor)) {
                    ghosts.add(toGhost(series, anchor));
                }
            }
        }

        var result = new ArrayList<ScheduleEntry>(realEntries.size() + ghosts.size());
        result.addAll(realEntries);
        result.addAll(ghosts);
        result.sort(Comparator.comparing(ScheduleEntry::scheduledAt));
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> activeMonths(String slug) {

        var organizationId = organizationContext.organizationId();
        var communityId = communityAccess.communityIdForSlug(slug);
        return sessionStore.findActiveMonths(organizationId, communityId);
    }

    private ScheduleEntry toEntry(Session session) {

        return ScheduleEntry.builder().sessionId(session.id()).seriesId(session.seriesId()).title(session.title())
                .occurrenceDate(session.occurrenceDate()).scheduledAt(session.scheduledAt()).hasTime(session.hasTime())
                .seriesTimeZone(session.seriesTimeZone()).durationMinutes(session.durationMinutes())
                .status(session.status()).place(session.place()).version(session.version())
                .updatedAt(session.updatedAt()).virtual(false).sections(session.sections()).build();
    }

    /**
     * A projected occurrence has no row of its own, so everything it reports is the series': the standing place with no
     * override to beat it, version {@code 0} because nothing about it has ever been revised, and the series'
     * {@code updatedAt} because a change to the series is the only revision a subscriber can observe.
     *
     * <p>
     * The place still goes through {@link MeetingPlaces} rather than being copied column by column, so a blank part is
     * absence here exactly as it is on the real entries of the same series. Reading the columns verbatim would make a
     * blank standing place an empty place here and no place at all there, so one schedule would answer "is there a
     * place" two different ways, which is the distinction {@code V58} made the columns nullable to preserve.
     */
    private ScheduleEntry toGhost(SeriesRecord series, LocalDate anchor) {

        var scheduledAt = OccurrenceInstant.at(anchor, series.getPreferredTime(), ZoneId.of(series.getTimeZone()));
        return ScheduleEntry.builder().seriesId(series.getId()).occurrenceDate(anchor).scheduledAt(scheduledAt)
                .hasTime(series.getPreferredTime() != null).seriesTimeZone(series.getTimeZone())
                .durationMinutes(series.getDurationMinutes()).status(SessionStatus.SCHEDULED)
                .place(MeetingPlaces.of(series)).version(0L).updatedAt(series.getUpdatedAt()).virtual(true)
                .sections(sessionMapper.projectSections(series)).build();
    }
}
