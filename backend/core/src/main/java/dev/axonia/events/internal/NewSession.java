package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.axonia.events.MeetingPlace;
import lombok.Builder;

/**
 * Everything a caller gets to decide about a session row that does not exist yet. What it deliberately does NOT carry
 * is anything {@link SessionCreation} is responsible for: {@code notes} and {@code recordingUrl} (both {@code NOT NULL}
 * and empty at birth in all three creation paths) and {@code upcomingReleaseAt}, which is the release intent derived
 * rather than accepted.
 *
 * <p>
 * A parameter record rather than a ten-argument factory method, per this repository's five-parameter ceiling.
 * {@code status} is a component rather than a constant because the three paths genuinely disagree: two open a
 * {@code SCHEDULED} session, and {@link dev.axonia.events.OccurrenceSkipService} materialises a row with no status at
 * all on its way to marking the occurrence {@code SKIPPED}.
 *
 * <p>
 * {@code timeZone} is the one-off's own clock and is null on the two occurrence paths, which read their series' zone
 * instead: an occurrence that disagreed with its series about what time it is would be a bug nobody could see.
 *
 * <p>
 * {@code hasTime} is likewise stated by the caller rather than derived from {@code scheduledAt} here. The instant of an
 * untimed occurrence is midnight in its series' zone, and that shape is not the fact: a series that genuinely meets at
 * midnight wears it too. Only the caller knows whether anybody chose an hour, so only the caller can say.
 */
@Builder
public record NewSession(String organizationId, Long seriesId, Long communityId, String title, String status,
        Instant scheduledAt, boolean hasTime, String timeZone, LocalDate occurrenceDate, Integer durationMinutes,
        MeetingPlace place, List<SectionEntity> sections) {

    public NewSession {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
