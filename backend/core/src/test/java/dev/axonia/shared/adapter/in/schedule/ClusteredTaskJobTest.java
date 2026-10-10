package dev.axonia.shared.adapter.in.schedule;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

import dev.axonia.shared.api.schedule.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusteredTaskJobTest {

    private final AtomicInteger runs = new AtomicInteger();

    private final ClusteredTask counting = new ClusteredTask("billing-sweep", new ClusteredTask.Cron("0 0 * * * ?"),
            runs::incrementAndGet);

    @Test
    void runsTheTaskItIsNamedFor() throws JobExecutionException {

        new ClusteredTaskJob(Map.of("billing-sweep", counting)).execute(contextFor("billing-sweep"));

        assertThat(runs).hasValue(1);
    }

    @Test
    void anUnknownTaskIsSkipped() throws JobExecutionException {

        new ClusteredTaskJob(Map.of("billing-sweep", counting)).execute(contextFor("standing-renewal"));

        assertThat(runs).hasValue(0);
    }

    @Test
    void aFailingTaskDoesNotRefire() {

        var failing = new ClusteredTask("billing-sweep", new ClusteredTask.Cron("0 0 * * * ?"), () -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(
                () -> new ClusteredTaskJob(Map.of("billing-sweep", failing)).execute(contextFor("billing-sweep")))
                .isInstanceOfSatisfying(JobExecutionException.class, e -> {
                    assertThat(e.refireImmediately()).isFalse();
                    assertThat(e.unscheduleFiringTrigger()).isFalse();
                    assertThat(e.unscheduleAllTriggers()).isFalse();
                    assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void aTaskNeverOverlapsItself() {

        assertThat(ClusteredTaskJob.class).hasAnnotation(DisallowConcurrentExecution.class);
    }

    private static JobExecutionContext contextFor(String name) {

        var context = mock(JobExecutionContext.class, Answers.RETURNS_DEEP_STUBS);
        when(context.getJobDetail().getKey()).thenReturn(JobKey.jobKey(name, ClusteredTaskJob.TASK_GROUP));
        return context;
    }
}
