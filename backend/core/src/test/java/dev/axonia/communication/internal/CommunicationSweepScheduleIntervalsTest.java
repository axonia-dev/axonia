package dev.axonia.communication.internal;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CommunicationSweep#drainTick}'s cadence at the property level: it reads its own key, with its own
 * default, rather than silently sharing the produce cadence's placeholder. A context-level test (booting a real
 * {@code TaskScheduler} and asserting on tick timing) would be slow and flaky by construction; the {@code @Scheduled}
 * SpEL strings are static once compiled, so reflection over the annotation itself is both cheaper and more direct.
 * {@link CommunicationSweepEnabledTest} still proves the property actually resolves inside a live context. The produce
 * and retention cadences are cluster-wide tasks now, pinned by {@link CommunicationTasksTest}.
 */
class CommunicationSweepScheduleIntervalsTest {

    @Test
    void drainTickDefaultsToSixtySeconds() throws NoSuchMethodException {

        var scheduled = scheduledOn("drainTick");

        assertThat(scheduled.fixedDelayString()).isEqualTo("${axonia.communication.drain-interval:60s}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${axonia.communication.drain-interval:60s}");
    }

    private static Scheduled scheduledOn(String methodName) throws NoSuchMethodException {

        Method method = CommunicationSweep.class.getDeclaredMethod(methodName);
        return method.getAnnotation(Scheduled.class);
    }
}
