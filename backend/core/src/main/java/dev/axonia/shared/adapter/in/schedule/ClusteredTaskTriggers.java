package dev.axonia.shared.adapter.in.schedule;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import dev.axonia.shared.api.schedule.ClusteredTask;
import dev.axonia.shared.api.schedule.ClusteredTask.Cron;
import dev.axonia.shared.api.schedule.ClusteredTask.Every;

/**
 * A {@link ClusteredTask}'s schedule as a Quartz trigger. A slot missed while no instance was up fires once on
 * recovery, never once per missed slot.
 */
final class ClusteredTaskTriggers {

    private static final TimeZone UTC = TimeZone.getTimeZone(ZoneOffset.UTC);

    private ClusteredTaskTriggers() {
    }

    static TriggerKey keyOf(ClusteredTask task) {

        return TriggerKey.triggerKey(task.name(), ClusteredTaskJob.TASK_GROUP);
    }

    static JobKey jobKeyOf(ClusteredTask task) {

        return JobKey.jobKey(task.name(), ClusteredTaskJob.TASK_GROUP);
    }

    static Trigger triggerFor(ClusteredTask task, Instant now) {

        var builder = TriggerBuilder.newTrigger().withIdentity(keyOf(task)).forJob(jobKeyOf(task));
        return switch (task.schedule()) {
            case Cron cron ->
                builder.startAt(Date.from(now)).withSchedule(CronScheduleBuilder.cronSchedule(cron.expression())
                        .inTimeZone(UTC).withMisfireHandlingInstructionFireAndProceed()).build();
            case Every every -> builder.startAt(Date.from(now.plus(every.initialDelay())))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(every.interval().toMillis()).repeatForever()
                            .withMisfireHandlingInstructionNowWithExistingCount())
                    .build();
        };
    }

    /**
     * Whether a stored trigger still runs {@code schedule}. An initial delay is not compared: it only means anything
     * for a trigger that does not exist yet.
     */
    static boolean matches(Trigger stored, ClusteredTask.Schedule schedule) {

        return switch (schedule) {
            case Cron cron ->
                stored instanceof CronTrigger trigger && trigger.getCronExpression().equals(cron.expression())
                        && UTC.getID().equals(trigger.getTimeZone().getID());
            case Every every ->
                stored instanceof SimpleTrigger trigger && trigger.getRepeatInterval() == every.interval().toMillis();
        };
    }
}
