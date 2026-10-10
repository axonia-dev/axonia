package dev.axonia.shared.adapter.in.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.transaction.support.TransactionOperations;

import dev.axonia.shared.api.schedule.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Runs against a real in-memory Quartz scheduler, so it asserts what Quartz stores rather than what was asked of it.
 */
class ClusteredTaskRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private Scheduler scheduler;

    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void ramScheduler() throws SchedulerException {

        var props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "registrar-test-" + System.nanoTime());
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        props.setProperty("org.quartz.threadPool.threadCount", "1");
        scheduler = new StdSchedulerFactory(props).getScheduler();
    }

    @AfterEach
    void shutdown() throws SchedulerException {

        scheduler.shutdown();
    }

    @Test
    void addsAJobAndATriggerPerTask() throws SchedulerException {

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ofMinutes(5))));

        assertThat(scheduler.checkExists(JobKey.jobKey("billing-sweep", ClusteredTaskJob.TASK_GROUP))).isTrue();
        assertThat(trigger("billing-sweep").getStartTime().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(((SimpleTrigger) trigger("billing-sweep")).getRepeatInterval())
                .isEqualTo(Duration.ofHours(1).toMillis());
    }

    @Test
    void anUnchangedScheduleKeepsItsStoredTrigger() throws SchedulerException {

        var every = new ClusteredTask.Every(Duration.ofHours(24), Duration.ofHours(24));
        register(task("billing-collection", every));
        var firstStart = trigger("billing-collection").getStartTime();

        clock = Clock.fixed(NOW.plus(Duration.ofHours(3)), ZoneOffset.UTC);
        register(task("billing-collection", every));

        assertThat(trigger("billing-collection").getStartTime()).isEqualTo(firstStart);
    }

    @Test
    void aChangedCronReplacesTheTrigger() throws SchedulerException {

        register(task("standing-renewal", new ClusteredTask.Cron("0 20 3 * * ?")));

        register(task("standing-renewal", new ClusteredTask.Cron("0 40 4 * * ?")));

        assertThat(((CronTrigger) trigger("standing-renewal")).getCronExpression()).isEqualTo("0 40 4 * * ?");
    }

    @Test
    void aChangedIntervalReplacesTheTrigger() throws SchedulerException {

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ofHours(1))));

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(2), Duration.ofHours(1))));

        assertThat(((SimpleTrigger) trigger("billing-sweep")).getRepeatInterval())
                .isEqualTo(Duration.ofHours(2).toMillis());
    }

    @Test
    void aTaskThatChangesKindReplacesTheTrigger() throws SchedulerException {

        register(task("standing-renewal", new ClusteredTask.Every(Duration.ofHours(24), Duration.ZERO)));

        register(task("standing-renewal", new ClusteredTask.Cron("0 20 3 * * ?")));

        assertThat(trigger("standing-renewal")).isInstanceOf(CronTrigger.class);
    }

    @Test
    void cronIsEvaluatedInUtc() throws SchedulerException {

        register(task("standing-renewal", new ClusteredTask.Cron("0 20 3 * * ?")));

        assertThat(((CronTrigger) trigger("standing-renewal")).getTimeZone().getID()).isEqualTo("UTC");
        assertThat(trigger("standing-renewal").getFireTimeAfter(java.util.Date.from(NOW)).toInstant())
                .isEqualTo(Instant.parse("2026-10-08T03:20:00Z"));
    }

    @Test
    void aMissedSlotFiresOnceOnRecovery() throws SchedulerException {

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO)),
                task("standing-renewal", new ClusteredTask.Cron("0 20 3 * * ?")));

        assertThat(trigger("billing-sweep").getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT);
        assertThat(trigger("standing-renewal").getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void aJobNoTaskDefinesIsDeleted() throws SchedulerException {

        register(task("sso-watch", new ClusteredTask.Every(Duration.ofHours(24), Duration.ofMinutes(10))));

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ofHours(1))));

        assertThat(scheduler.checkExists(JobKey.jobKey("sso-watch", ClusteredTaskJob.TASK_GROUP))).isFalse();
        assertThat(scheduler.checkExists(JobKey.jobKey("billing-sweep", ClusteredTaskJob.TASK_GROUP))).isTrue();
    }

    /**
     * A job stored by an earlier version names whatever class ran it then, and a release that moves
     * {@link ClusteredTaskJob} leaves that name behind. Registration rewrites every task's job, so the next fire runs
     * the class this version ships.
     */
    @Test
    void aStoredJobOfAnotherClassIsReplacedByTheTaskJob() throws SchedulerException {

        scheduler.addJob(org.quartz.JobBuilder.newJob(EarlierVersionJob.class)
                .withIdentity("billing-sweep", ClusteredTaskJob.TASK_GROUP).storeDurably().build(), false);

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ofHours(1))));

        assertThat(scheduler.getJobDetail(JobKey.jobKey("billing-sweep", ClusteredTaskJob.TASK_GROUP)).getJobClass())
                .isEqualTo(ClusteredTaskJob.class);
    }

    @Test
    void aJobOutsideTheTaskGroupIsLeftAlone() throws SchedulerException {

        scheduler.addJob(org.quartz.JobBuilder.newJob(ClusteredTaskJob.class).withIdentity("other", "elsewhere")
                .storeDurably().build(), false);

        register(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ofHours(1))));

        assertThat(scheduler.checkExists(JobKey.jobKey("other", "elsewhere"))).isTrue();
    }

    @Test
    void aSchedulerFailureBecomesAnIllegalStateWithItsCauseKept() throws SchedulerException {

        var failure = new SchedulerException("store is down");
        var failing = mock(Scheduler.class);
        doThrow(failure).when(failing).addJob(any(), anyBoolean());
        var registrar = new ClusteredTaskRegistrar(failing,
                List.of(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO))), clock,
                TransactionOperations.withoutTransaction());

        assertThatThrownBy(registrar::register).isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not register the clustered tasks").hasCause(failure);
    }

    @Test
    void recoveringLeavesATriggerThatIsNotInErrorAlone() throws SchedulerException {

        var task = task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO));
        register(task);
        scheduler.pauseTrigger(TriggerKey.triggerKey("billing-sweep", ClusteredTaskJob.TASK_GROUP));

        new ClusteredTaskRegistrar(scheduler, List.of(task), clock, TransactionOperations.withoutTransaction())
                .recover();

        assertThat(scheduler.getTriggerState(TriggerKey.triggerKey("billing-sweep", ClusteredTaskJob.TASK_GROUP)))
                .isEqualTo(Trigger.TriggerState.PAUSED);
    }

    @Test
    void aSchedulerFailureDuringRecoveryBecomesAnIllegalStateWithItsCauseKept() throws SchedulerException {

        var failure = new SchedulerException("store is down");
        var failing = mock(Scheduler.class);
        doThrow(failure).when(failing).getTriggerState(any());
        var registrar = new ClusteredTaskRegistrar(failing,
                List.of(task("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO))), clock,
                TransactionOperations.withoutTransaction());

        assertThatThrownBy(registrar::recover).isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not recover the clustered tasks").hasCause(failure);
    }

    private void register(ClusteredTask... tasks) throws SchedulerException {

        new ClusteredTaskRegistrar(scheduler, List.of(tasks), clock, TransactionOperations.withoutTransaction())
                .register();
    }

    private Trigger trigger(String name) throws SchedulerException {

        return scheduler.getTrigger(TriggerKey.triggerKey(name, ClusteredTaskJob.TASK_GROUP));
    }

    private static ClusteredTask task(String name, ClusteredTask.Schedule schedule) {

        return new ClusteredTask(name, schedule, () -> {
        });
    }

    static class EarlierVersionJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}
