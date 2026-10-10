package dev.axonia.shared.configuration;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduler a test context builds never starts and never registers a job: contexts share one database, so a context
 * that registered its own tasks would delete every other context's.
 */
@CoreSpringBootTest
@ActiveProfiles("DEV")
class SchedulerOffInTestsTest {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private ApplicationContext context;

    @Test
    void theSchedulerIsNotStarted() throws SchedulerException {

        assertThat(scheduler.isStarted()).isFalse();
    }

    @Test
    void noJobIsRegistered() throws SchedulerException {

        assertThat(scheduler.getJobKeys(GroupMatcher.anyGroup())).isEmpty();
        assertThat(context.containsBean("clusteredTaskRegistration")).isFalse();
        assertThat(context.containsBean("clusteredTaskRecovery")).isFalse();
    }

    @Test
    void quartzRunsOnTheClusteredJdbcStore() throws SchedulerException {

        assertThat(scheduler.getMetaData().isJobStoreClustered()).isTrue();
        assertThat(scheduler.getMetaData().getJobStoreClass().getSimpleName()).isEqualTo("LocalDataSourceJobStore");
        assertThat(context.getBeansOfType(ApplicationRunner.class)).doesNotContainKey("clusteredTaskRegistration");
    }

    @Test
    void theApplicationKeepsItsOwnTransactionManager() {

        assertThat(context.containsBean("transactionManager")).isTrue();
    }
}
