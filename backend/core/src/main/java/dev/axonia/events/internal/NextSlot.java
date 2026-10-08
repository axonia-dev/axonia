package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.axonia.events.Cadence;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NextSlot {

    private static final int MAX_STEPS = 1200;

    public Optional<Instant> suggest(NextSlotQuery query) {

        var step = query.cadence().toPeriod();
        var date = query.startsOn();
        for (var i = 0; i < MAX_STEPS; i++) {
            var candidate = OccurrenceInstant.at(date, query.preferredTime(), query.zone());
            if (!candidate.isBefore(query.now())) {
                return Optional.of(candidate);
            }
            date = date.plus(step);
        }
        return Optional.empty();
    }

    public List<LocalDate> occurrencesBetween(Cadence cadence, LocalDate startsOn, LocalDate from, LocalDate to) {

        var step = cadence.toPeriod();
        var result = new ArrayList<LocalDate>();
        var date = startsOn;
        for (var i = 0; i < MAX_STEPS && date.isBefore(to); i++) {
            if (!date.isBefore(from)) {
                result.add(date);
            }
            date = date.plus(step);
        }
        return result;
    }
}
