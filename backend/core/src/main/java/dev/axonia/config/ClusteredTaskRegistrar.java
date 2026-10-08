package dev.axonia.config;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.transaction.support.TransactionOperations;

import dev.axonia.ClusteredTask;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings the scheduler's stored jobs in line with the {@link ClusteredTask} beans of this instance, once on start.
 *
 * <p>
 * A stored trigger is replaced only when its schedule changed, so a deploy never resets a timer: a daily task keeps its
 * next fire time across every restart. A trigger Quartz left in error is put back. A stored job no task defines any
 * more is deleted.
 *
 * <p>
 * It runs in one transaction on the scheduler's own pool, so Quartz's lock row is held until it commits: instances
 * starting together register one after the other instead of inserting the same job twice.
 */
@Slf4j
public class ClusteredTaskRegistrar {

    private final Scheduler scheduler;

    private final List<ClusteredTask> tasks;

    private final Clock clock;

    private final TransactionOperations transactions;

    public ClusteredTaskRegistrar(Scheduler scheduler, List<ClusteredTask> tasks, Clock clock,
            TransactionOperations transactions) {

        this.scheduler = scheduler;
        this.tasks = List.copyOf(tasks);
        this.clock = clock;
        this.transactions = transactions;
    }

    public void register() {

        transactions.executeWithoutResult(status -> {
            try {
                registerAll();
            } catch (SchedulerException e) {
                throw new IllegalStateException("Could not register the clustered tasks", e);
            }
        });
    }

    private void registerAll() throws SchedulerException {

        for (var task : tasks) {
            scheduler.addJob(JobBuilder.newJob(ClusteredTaskJob.class)
                    .withIdentity(ClusteredTaskTriggers.jobKeyOf(task)).storeDurably().build(), true);
            schedule(task);
        }
        deleteStale();
    }

    private void schedule(ClusteredTask task) throws SchedulerException {

        var stored = scheduler.getTrigger(ClusteredTaskTriggers.keyOf(task));
        if (stored == null) {
            scheduler.scheduleJob(ClusteredTaskTriggers.triggerFor(task, clock.instant()));
        } else if (!ClusteredTaskTriggers.matches(stored, task.schedule())) {
            log.info("Rescheduling clustered task {}: its schedule changed", task.name());
            scheduler.rescheduleJob(stored.getKey(), ClusteredTaskTriggers.triggerFor(task, clock.instant()));
        } else if (scheduler.getTriggerState(stored.getKey()) == Trigger.TriggerState.ERROR) {
            log.warn("Resetting clustered task {}: Quartz left its trigger in error", task.name());
            scheduler.resetTriggerFromErrorState(stored.getKey());
        }
    }

    private void deleteStale() throws SchedulerException {

        Set<String> defined = tasks.stream().map(ClusteredTask::name).collect(Collectors.toSet());
        for (JobKey stored : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(ClusteredTaskJob.TASK_GROUP))) {
            if (!defined.contains(stored.getName())) {
                log.info("Deleting clustered task {}: no instance of this version defines it", stored.getName());
                scheduler.deleteJob(stored);
            }
        }
    }
}
