package dev.axonia.config;

import java.util.Map;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;

import dev.axonia.ClusteredTask;
import lombok.extern.slf4j.Slf4j;

/**
 * The one Quartz job every {@link ClusteredTask} runs through.
 *
 * <p>
 * While a rolling deploy is under way an instance can acquire a job that only the newer code defines. It skips it, and
 * the next slot lands wherever the trigger goes next. A task that throws is reported to Quartz as a failure that must
 * not refire: its next slot is its next chance.
 */
@DisallowConcurrentExecution
@Slf4j
public class ClusteredTaskJob implements Job {

    public static final String TASK_GROUP = "axonia";

    private final Map<String, ClusteredTask> tasks;

    public ClusteredTaskJob(@Qualifier("clusteredTasks") Map<String, ClusteredTask> tasks) {

        this.tasks = Map.copyOf(tasks);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        var name = context.getJobDetail().getKey().getName();
        var task = tasks.get(name);
        if (task == null) {
            log.warn("Skipping clustered task {}: this instance does not define it", name);
            return;
        }
        try {
            task.run().run();
        } catch (RuntimeException e) {
            throw new JobExecutionException(e, false);
        }
    }
}
