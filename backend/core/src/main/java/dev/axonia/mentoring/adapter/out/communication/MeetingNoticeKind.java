package dev.axonia.mentoring.adapter.out.communication;

import java.util.Arrays;

import dev.axonia.mentoring.domain.PlannedMeeting;

/**
 * Whether a meeting notice announced a plan or a move. Both share one message type, so the subject's occasion names the
 * kind with the planning's moment, as {@code planned-<epoch millis>} or {@code moved-<epoch millis>}: withdrawing one
 * never reaches the other, and a move made before a move's notice left stays a move.
 */
enum MeetingNoticeKind {

    PLANNED("planned", "Meeting planned by "), MOVED("moved", "Meeting moved by ");

    private final String prefix;
    private final String headline;

    MeetingNoticeKind(String prefix, String headline) {

        this.prefix = prefix;
        this.headline = headline;
    }

    String occasionOf(PlannedMeeting meeting) {

        return prefix + '-' + meeting.plannedAt().toEpochMilli();
    }

    String headline(String byName) {

        return headline + byName;
    }

    /** Whether the occasion names this very planning, of either kind. */
    static boolean namesThePlanning(PlannedMeeting meeting, String occasion) {

        return Arrays.stream(values()).anyMatch(kind -> kind.occasionOf(meeting).equals(occasion));
    }
}
