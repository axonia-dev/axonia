package dev.axonia.mentoring.domain;

import java.util.Optional;

/**
 * Who is listed among a community's mentors. A shown pairing lists its mentor unless they switched it off; an ended
 * pairing that never showed lists them only once they switched it on. An ongoing one makes them Mentoring now.
 */
public final class MentorRoster {

    public enum Status {
        MENTORING_NOW, MENTORED_HERE
    }

    private MentorRoster() {
    }

    public static Optional<Status> status(MentorHistory history, ListingSwitch listing) {

        if (listing == ListingSwitch.OFF) {
            return Optional.empty();
        }
        if (history.ongoingShown()) {
            return Optional.of(Status.MENTORING_NOW);
        }
        if (history.endedShown() || history.endedUnshown() && listing == ListingSwitch.ON) {
            return Optional.of(Status.MENTORED_HERE);
        }
        return Optional.empty();
    }

    /** Only an ended, never shown pairing would list them, and they have not answered yet. */
    public static boolean needsAsk(MentorHistory history, ListingSwitch listing) {

        return listing == ListingSwitch.DEFAULT && history.endedUnshown() && !history.ongoingShown()
                && !history.endedShown();
    }
}
