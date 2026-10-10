package dev.axonia.identity.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.identity.adapter.in.schedule.AgentConnectionRetentionSweep;
import dev.axonia.shared.api.schedule.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentConnectionTasksTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AgentConnectionRetentionSweep.class, () -> mock(AgentConnectionRetentionSweep.class))
            .withUserConfiguration(AgentConnectionTasks.class);

    @Test
    void retentionDefaultsToDailyAfterTwentyMinutes() {

        runner.run(context -> assertThat(schedule(context))
                .isEqualTo(new ClusteredTask.Every(Duration.ofHours(24), Duration.ofMinutes(20))));
    }

    @Test
    void eachDelayReadsItsOwnKey() {

        runner.withPropertyValues("axonia.identity.agent-connection-retention.interval=48h",
                "axonia.identity.agent-connection-retention.initial-delay=1m")
                .run(context -> assertThat(schedule(context))
                        .isEqualTo(new ClusteredTask.Every(Duration.ofHours(48), Duration.ofMinutes(1))));
    }

    @Test
    void theTaskIsNamedAfterItsSweepAndRunsItsTick() {

        runner.run(context -> {
            var task = context.getBean("agentConnectionRetentionTask", ClusteredTask.class);

            task.run().run();

            assertThat(task.name()).isEqualTo("agent-connection-retention");
            verify(context.getBean(AgentConnectionRetentionSweep.class)).tick();
        });
    }

    private static ClusteredTask.Schedule schedule(AssertableApplicationContext context) {

        return context.getBean("agentConnectionRetentionTask", ClusteredTask.class).schedule();
    }
}
