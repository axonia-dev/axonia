package dev.axonia.mentoring.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

/**
 * What a pairing's plan of meetings allows: one meeting a day, a day from today to a year ahead, a few board topics
 * each, and twelve meetings ahead at most. A meeting no note has held is upcoming from its day on and owed notes after
 * it; a held one has left the plan.
 */
public final class MeetingPlan {

    public static final int MAX_AHEAD = 12;

    public static final int MAX_TOPICS = 5;

    public static final int WINDOW_DAYS = 366;

    private static final String TOPICS = "MENTORING_MEETING_TOPICS";

    private MeetingPlan() {
    }

    public static LocalDate plannable(LocalDate on, LocalDate today) {

        if (on == null) {
            throw new ValidationException("field_invalid", "A meeting needs a day");
        }
        if (on.isBefore(today)) {
            throw new ValidationException("MENTORING_MEETING_PAST", "Pick today or a later day.");
        }
        if (on.isAfter(today.plusDays(WINDOW_DAYS))) {
            throw new ValidationException("MENTORING_MEETING_TOO_FAR", "Pick a day within a year.");
        }
        return on;
    }

    public static boolean isUpcoming(PlannedMeeting meeting, LocalDate today) {

        return !meeting.held() && !meeting.on().isBefore(today);
    }

    public static boolean isOwed(PlannedMeeting meeting, LocalDate today) {

        return !meeting.held() && meeting.on().isBefore(today);
    }

    public static void requireFree(List<PlannedMeeting> meetings, LocalDate day) {

        if (meetings.stream().anyMatch(m -> m.on().equals(day))) {
            throw new ConflictException("MENTORING_MEETING_TAKEN", "A meeting is already planned for that day.");
        }
    }

    public static void requireRoom(List<PlannedMeeting> meetings, LocalDate today) {

        if (meetings.stream().filter(m -> isUpcoming(m, today)).count() >= MAX_AHEAD) {
            throw new ConflictException("MENTORING_MEETING_LIMIT",
                    "Twelve meetings are planned already. Remove one first.");
        }
    }

    /** The topics asked for, each once, all of them on the pairing's board. */
    public static List<Long> topics(List<Long> requested, Set<Long> board) {

        var topics = requested == null ? List.<Long>of() : requested.stream().distinct().toList();
        if (topics.size() > MAX_TOPICS) {
            throw new ValidationException(TOPICS, "A meeting names at most five topics.");
        }
        if (!board.containsAll(topics)) {
            throw new ValidationException(TOPICS, "A meeting names topics of its own journal.");
        }
        return topics;
    }
}
