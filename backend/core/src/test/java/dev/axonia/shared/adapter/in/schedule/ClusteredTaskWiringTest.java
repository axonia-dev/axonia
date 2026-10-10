package dev.axonia.shared.adapter.in.schedule;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.shared.api.schedule.ClusteredTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The scheduler as the application starts it: registration on start, and a fired job reaching its task through the job
 * factory Spring Boot gives Quartz. Its own database, because a started scheduler runs the application's tasks against
 * whatever database it is given.
 */
@CoreSpringBootTest(properties = "spring.quartz.auto-startup=true")
@ActiveProfiles("DEV")
@Testcontainers
@DirtiesContext
@Import(ClusteredTaskWiringTest.Probe.class)
class ClusteredTaskWiringTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    private static final String PROBE = "wiring-probe";

    private static final AtomicInteger PROBE_RUNS = new AtomicInteger();

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private List<ClusteredTask> tasks;

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {

        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("axonia.tenant-discovery.datasource.url", PG::getJdbcUrl);
        registry.add("axonia.tenant-discovery.datasource.username", () -> "axonia_communication_read");
        registry.add("axonia.tenant-discovery.datasource.password", () -> "communication_read");
        registry.add("axonia.tenant-discovery.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void everyTaskOfTheApplicationIsRegisteredOnStart() throws SchedulerException {

        var registered = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(ClusteredTaskJob.TASK_GROUP)).stream()
                .map(JobKey::getName).toList();

        assertThat(registered).containsExactlyInAnyOrderElementsOf(tasks.stream().map(ClusteredTask::name).toList())
                .contains(PROBE);
    }

    @Test
    void aFiredJobRunsItsTask() throws SchedulerException {

        scheduler.triggerJob(JobKey.jobKey(PROBE, ClusteredTaskJob.TASK_GROUP));

        await().atMost(Duration.ofSeconds(30)).until(() -> PROBE_RUNS.get() >= 1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probe {

        @Bean
        ClusteredTask wiringProbe() {
            return new ClusteredTask(PROBE, new ClusteredTask.Every(Duration.ofDays(1), Duration.ofDays(1)),
                    PROBE_RUNS::incrementAndGet);
        }
    }
}
