package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NextSlot {

    private static final int MAX_STEPS = 1200;

    public Optional<Instant> suggest(NextSlotQuery query) {

        for (var index = 0; index < MAX_STEPS; index++) {
            var date = query.cadence().occurrence(query.startsOn(), index);
            var candidate = OccurrenceInstant.at(date, query.preferredTime(), query.zone());
            if (!candidate.isBefore(query.now())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public List<LocalDate> occurrencesBetween(Cadence cadence, LocalDate startsOn, LocalDate from, LocalDate to) {

        var result = new ArrayList<LocalDate>();
        for (var index = 0; index < MAX_STEPS; index++) {
            var date = cadence.occurrence(startsOn, index);
            if (!date.isBefore(to)) {
                break;
            }
            if (!date.isBefore(from)) {
                result.add(date);
            }
        }
        return result;
    }
}
