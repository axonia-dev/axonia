package dev.axonia.events.internal;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.events.ScheduledAt;
import lombok.RequiredArgsConstructor;

/**
 * Which zone governs a session's wall clock, and therefore what instant a stated date and time actually name.
 *
 * <p>
 * {@link OccurrenceInstant} already owns the arithmetic; what this adds is the answer to "in whose zone". An occurrence
 * of a series composes in its series' zone and the zone the request states is ignored, so no client can reschedule a
 * community's session into a zone the community did not choose - the defect that let an organizer working abroad move a
 * session by an unnoticed offset. A session with no series has no such zone, so there the caller's is the only one
 * there is, and it is honoured.
 *
 * <p>
 * A collaborator rather than two private methods on {@code SessionService}, because the rule is a domain decision with
 * a name and that class is already at this repository's class-complexity ceiling. Both entry points live here together
 * so the create path and the edit path cannot come to disagree about whether a session with no series may be untimed.
 */
@Component
@RequiredArgsConstructor
public class SessionInstant {

    private final SeriesRepository seriesRepository;

    /**
     * The instant a stated date and time name, in the zone that governs this session: its series' when it has one, the
     * caller's when it does not.
     */
    @Transactional(readOnly = true)
    public Instant of(SessionEntity session, ScheduledAt at) {

        if (session.getSeriesId() == null) {
            return ofOneOff(at);
        }
        return OccurrenceInstant.at(at.date(), at.time(), zoneOf(session));
    }

    /**
     * The same, for a session that has no series and no row yet. A one-off must state a time, because "untimed" is a
     * property of an occurrence of a cadence and a one-off has no cadence to be untimed within.
     */
    public Instant ofOneOff(ScheduledAt at) {

        if (at.time() == null) {
            throw new ValidationException("SESSION_TIME_REQUIRED", "A one-off session must have a time");
        }
        return OccurrenceInstant.at(at.date(), at.time(), ZoneId.of(at.zone()));
    }

    private ZoneId zoneOf(SessionEntity session) {

        return seriesRepository.findByOrganizationIdAndId(session.getOrganizationId(), session.getSeriesId())
                .map(series -> ZoneId.of(series.getTimeZone()))
                .orElseThrow(() -> new NotFoundException("Series not found: " + session.getSeriesId()));
    }
}
