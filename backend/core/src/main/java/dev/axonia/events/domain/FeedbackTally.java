package dev.axonia.events.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sums on the ten-point range; each average reads only the answers that hold its question. */
public record FeedbackTally(long answers, long enjoyedAnswers, double enjoyedSum, long learnedAnswers,
        double learnedSum) {

    public Double enjoyedAverage() {

        return average(enjoyedSum, enjoyedAnswers);
    }

    public Double learnedAverage() {

        return average(learnedSum, learnedAnswers);
    }

    private static Double average(double sum, long count) {

        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
