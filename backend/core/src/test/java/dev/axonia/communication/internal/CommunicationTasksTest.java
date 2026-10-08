package dev.axonia.communication.internal;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import dev.axonia.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Each cluster-wide cadence reads its own key, with its own default. {@code eachCadenceReadsItsOwnKey} catches a
 * copy-pasted key that the default-value tests cannot, because a shared key would still match one default.
 */
class CommunicationTasksTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CommunicationSweep.class, () -> mock(CommunicationSweep.class))
            .withUserConfiguration(CommunicationTasks.class);

    @Test
    void produceDefaultsToEveryFiveMinutes() {

        runner.run(context -> assertThat(schedule(context, "communicationProduceTask"))
                .isEqualTo(new ClusteredTask.Every(Duration.ofMinutes(5), Duration.ofMinutes(5))));
    }

    @Test
    void retentionDefaultsToDailyAfterTenMinutes() {

        runner.run(context -> assertThat(schedule(context, "communicationRetentionTask"))
                .isEqualTo(new ClusteredTask.Every(Duration.ofHours(24), Duration.ofMinutes(10))));
    }

    @Test
    void eachCadenceReadsItsOwnKey() {

        runner.withPropertyValues("axonia.communication.produce-interval=7m",
                "axonia.communication.retention-interval=48h", "axonia.communication.retention-initial-delay=1m")
                .run(context -> {
                    assertThat(schedule(context, "communicationProduceTask"))
                            .isEqualTo(new ClusteredTask.Every(Duration.ofMinutes(7), Duration.ofMinutes(7)));
                    assertThat(schedule(context, "communicationRetentionTask"))
                            .isEqualTo(new ClusteredTask.Every(Duration.ofHours(48), Duration.ofMinutes(1)));
                });
    }

    @Test
    void eachTaskIsNamedAfterItsSweep() {

        runner.run(context -> {
            assertThat(context.getBean("communicationProduceTask", ClusteredTask.class).name())
                    .isEqualTo("communication-produce");
            assertThat(context.getBean("communicationRetentionTask", ClusteredTask.class).name())
                    .isEqualTo("communication-retention");
        });
    }

    @Test
    void eachTaskRunsItsOwnTick() {

        runner.run(context -> {
            var sweep = context.getBean(CommunicationSweep.class);

            context.getBean("communicationProduceTask", ClusteredTask.class).run().run();
            verify(sweep).produceTick();
            verifyNoMoreInteractions(sweep);

            context.getBean("communicationRetentionTask", ClusteredTask.class).run().run();
            verify(sweep).retentionTick();
            verifyNoMoreInteractions(sweep);
        });
    }

    @Test
    void theTasksAreAbsentWhenTheSweepIsDisabled() {

        runner.withPropertyValues("axonia.communication.sweep.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ClusteredTask.class));
    }

    private static ClusteredTask.Schedule schedule(AssertableApplicationContext context, String bean) {

        return context.getBean(bean, ClusteredTask.class).schedule();
    }
}
