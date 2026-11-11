package dev.axonia.events.api;

import java.time.Instant;

import lombok.Builder;

/** How one caller sees a session's feedback: whether it can still be given, their own answer, and how it landed. */
@Builder
public record SessionFeedbackView(Window window, Instant closesAt, Role role, FeedbackAnswer mine, Result result) {

    public enum Window {
        OPEN, CLOSED, NONE
    }

    public enum Role {
        ATTENDEE, PRESENTER, OTHER
    }

    public enum ResultState {
        PENDING, TOO_FEW, REVEALED
    }

    @Builder
    public record Result(ResultState state, long answers, Double enjoyed, Double learned, String importedFrom) {
    }
}
