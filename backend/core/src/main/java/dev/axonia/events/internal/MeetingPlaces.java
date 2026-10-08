package dev.axonia.events.internal;

import dev.axonia.events.MeetingPlace;
import lombok.experimental.UtilityClass;

/**
 * Reads a stored place off a row. The three columns are separate because SQL has no record type, and this is the one
 * place they are put back together, so no mapper reconstructs the value with its parts in a different order.
 */
@UtilityClass
public class MeetingPlaces {

    public MeetingPlace of(SessionEntity e) {

        return new MeetingPlace(e.getLocation(), e.getJoinUrl(), e.getJoiningDetails());
    }

    public MeetingPlace of(SeriesEntity e) {

        return new MeetingPlace(e.getLocation(), e.getJoinUrl(), e.getJoiningDetails());
    }
}
