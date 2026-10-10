package dev.axonia.shared.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code spring.task.scheduling.shutdown.await-termination(-period)} in {@code application.properties} does nothing
 * until a {@code @Scheduled} method exists: only then does
 * {@link org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration} activate and apply them. This
 * test verifies that they really are bound rather than trusting that they are.
 *
 * <p>
 * {@code spring.threads.virtual.enabled=true} (also in {@code application.properties}) is what routes Boot's
 * autoconfiguration to build a {@link SimpleAsyncTaskScheduler} rather than a {@code ThreadPoolTaskScheduler}; only
 * that path is asserted here, since it is the one this application actually runs.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class SchedulingShutdownConfiguredTest {

    @Autowired
    private TaskSchedulingProperties properties;

    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    void theShutdownPropertiesFromApplicationPropertiesAreBoundOntoTheAutoConfiguredScheduler() {

        assertThat(properties.getShutdown().isAwaitTermination()).isTrue();
        assertThat(properties.getShutdown().getAwaitTerminationPeriod()).isEqualTo(Duration.ofSeconds(20));

        // then - @EnableScheduling actually triggered TaskSchedulingAutoConfiguration's virtual-thread path, the
        // one Boot applies the shutdown properties onto via SimpleAsyncTaskSchedulerBuilder.
        assertThat(taskScheduler).isInstanceOf(SimpleAsyncTaskScheduler.class);
    }
}
