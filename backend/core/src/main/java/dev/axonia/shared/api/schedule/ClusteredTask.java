package dev.axonia.shared.api.schedule;

import java.time.Duration;

import org.quartz.CronExpression;

import dev.axonia.shared.api.metrics.MeterVocabulary;

/**
 * Work that must happen once across every running instance, contributed by a module as a bean and run by the clustered
 * scheduler. Work each instance does for itself stays on {@code @Scheduled}.
 *
 * <p>
 * {@code name} is the job's identity in the scheduler's tables and its {@link SweepHeartbeat.Sweep} tag. Once deployed
 * it is never renamed, because a renamed task is a deleted job plus a new one whose timer starts over.
 */
public record ClusteredTask(String name, Schedule schedule, Runnable run) {

    public ClusteredTask {

        MeterVocabulary.requireTag(name);
    }

    public sealed interface Schedule permits Cron, Every {
    }

    /** A Quartz cron expression, evaluated in UTC. */
    public record Cron(String expression) implements Schedule {

        public Cron {

            if (!CronExpression.isValidExpression(expression)) {
                throw new IllegalArgumentException("Not a Quartz cron expression: " + expression);
            }
        }
    }

    public record Every(Duration interval, Duration initialDelay) implements Schedule {

        public Every {

            if (!interval.isPositive() || initialDelay.isNegative()) {
                throw new IllegalArgumentException("A schedule needs a positive interval and no negative delay");
            }
        }
    }
}
