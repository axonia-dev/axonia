package dev.axonia.events.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Session;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.NewSession;
import dev.axonia.events.domain.OccurrenceInstant;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionSkipped;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Owns the skip/unskip lifecycle of a series occurrence: marking a future occurrence as not-happening and restoring it.
 * Split from {@link SessionService} so each class keeps a single, bounded responsibility.
 */
@Service
@RequiredArgsConstructor
public class OccurrenceSkipService {

    private final SessionStore sessionStore;
    private final SeriesStore seriesStore;
    private final SessionMapper sessionMapper;
    private final SessionCreation sessionCreation;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ActionItemStore actionItemStore;

    @Transactional
    public Session skip(Long seriesId, LocalDate occurrenceDate) {

        var organizationId = organizationContext.organizationId();
        var now = clock.instant();
        var series = series(organizationId, seriesId);
        if (!communityAccess.isOrganizer(series.getCommunityId(), currentUser.userId())) {
            throw new ForbiddenException("Organizer required");
        }
        var existing = sessionStore.findByOrganizationIdAndSeriesIdAndOccurrenceDate(organizationId, seriesId,
                occurrenceDate);
        // Both refusals are answered from the occurrence itself, before any row is materialised: creating one costs a
        // read of the community's communication settings (SessionCreation stamps the upcoming release from them), and
        // a request that is about to 409 has no business paying for it.
        var scheduledAt = existing.map(SessionRecord::getScheduledAt).orElseGet(
                () -> OccurrenceInstant.at(occurrenceDate, series.getPreferredTime(), ZoneId.of(series.getTimeZone())));
        if (existing.filter(sessionMapper::isHeld).isPresent()) {
            throw new ConflictException("SESSION_ALREADY_HELD", "Cannot skip a session that has already been held");
        }
        // SKIPPED cannot be applied retroactively: once the clock has passed an occurrence
        // unanswered, only cancel (of a materialized row) can say "no, it did not happen" - skip is for calling one
        // off in advance. Driven by the injected Clock, never derived from isHeld, which no longer means "past".
        if (!scheduledAt.isAfter(now)) {
            throw new ConflictException("OCCURRENCE_IN_PAST", "Cannot skip a past occurrence; cancel it instead");
        }
        var entity = existing.orElseGet(() -> sessionCreation.newSession(NewSession.builder()
                .organizationId(organizationId).seriesId(seriesId).communityId(series.getCommunityId())
                .occurrenceDate(occurrenceDate).scheduledAt(scheduledAt).hasTime(series.getPreferredTime() != null)
                .durationMinutes(series.getDurationMinutes()).sections(List.of()).build()));
        var saved = sessionStore.save(entity.toBuilder().status("SKIPPED").build());
        // A skipped occurrence answers nothing either. Clearing is deliberately not reversed by unskip: the item has
        // late-bound to whatever came next in the meantime, and re-pinning it would silently override that.
        actionItemStore.clearPinsFor(organizationId, saved.getCommunityId(), saved.getId(), now);
        var result = sessionMapper.toSession(saved);
        eventPublisher.publishEvent(new SessionSkipped(result.id(), result.communityId(), result.occurrenceDate()));
        return result;
    }

    @Transactional
    public Session unskip(Long seriesId, LocalDate occurrenceDate) {

        var organizationId = organizationContext.organizationId();
        var series = series(organizationId, seriesId);
        if (!communityAccess.isOrganizer(series.getCommunityId(), currentUser.userId())) {
            throw new ForbiddenException("Organizer required");
        }
        var entity = sessionStore
                .findByOrganizationIdAndSeriesIdAndOccurrenceDate(organizationId, seriesId, occurrenceDate)
                .orElseThrow(() -> new NotFoundException("Occurrence not found: " + seriesId + "@" + occurrenceDate));
        if (!"SKIPPED".equals(entity.getStatus())) {
            throw new ConflictException("SESSION_NOT_SKIPPED", "This occurrence is not skipped");
        }
        var current = Objects.requireNonNullElse(entity.getSections(), List.<SectionRecord>of());
        var sections = current.isEmpty() ? sessionMapper.snapshotSections(series) : current;
        return sessionMapper
                .toSession(sessionStore.save(entity.toBuilder().status("SCHEDULED").sections(sections).build()));
    }

    private SeriesRecord series(String organizationId, Long seriesId) {

        return seriesStore.findByOrganizationIdAndId(organizationId, seriesId)
                .orElseThrow(() -> new NotFoundException("Series not found: " + seriesId));
    }
}
