package dev.axonia.health.api;

/**
 * How held sessions felt over a window, from the events context: answer rows, per-question counts and sums on the
 * ten-point range over revealed sessions only, so a session still collecting answers never moves a reading.
 * {@code sessionsTooFew} counts sessions that closed with some answers but fewer than the reveal minimum. Counts and
 * sums only, never who.
 */
public record FeedbackAverages(long answers, long enjoyedAnswers, double enjoyedSum, long learnedAnswers,
        double learnedSum, int sessions, int sessionsTooFew) {

    public static final FeedbackAverages NONE = new FeedbackAverages(0, 0, 0, 0, 0, 0, 0);
}
