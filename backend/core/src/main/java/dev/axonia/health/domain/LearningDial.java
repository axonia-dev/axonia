package dev.axonia.health.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.axonia.health.api.FeedbackAverages;
import lombok.Builder;

/**
 * In the room: how revealed sessions held in the window felt, each average to one decimal over only the answers that
 * hold its question, null when there are none; {@code answers} counts the answer rows, however many questions each one
 * holds. The previous averages read the window before, revealed as of now.
 */
@Builder
public record LearningDial(Double enjoyed, Double learned, long answers, long enjoyedAnswers, long learnedAnswers,
        int sessions, int sessionsTooFew, Double previousEnjoyed, Double previousLearned, Status status) {

    /** What a snapshot assembled without feedback carries: no answers, too few to read. */
    public static LearningDial empty() {

        return of(FeedbackAverages.NONE, FeedbackAverages.NONE);
    }

    public static LearningDial of(FeedbackAverages current, FeedbackAverages previous) {

        var learned = average(current.learnedSum(), current.learnedAnswers());
        return builder().enjoyed(average(current.enjoyedSum(), current.enjoyedAnswers())).learned(learned)
                .answers(current.answers()).enjoyedAnswers(current.enjoyedAnswers())
                .learnedAnswers(current.learnedAnswers()).sessions(current.sessions())
                .sessionsTooFew(current.sessionsTooFew())
                .previousEnjoyed(average(previous.enjoyedSum(), previous.enjoyedAnswers()))
                .previousLearned(average(previous.learnedSum(), previous.learnedAnswers()))
                .status(StatusRules.learning(learned, current.learnedAnswers())).build();
    }

    private static Double average(double sum, long answers) {

        if (answers == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(answers), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
