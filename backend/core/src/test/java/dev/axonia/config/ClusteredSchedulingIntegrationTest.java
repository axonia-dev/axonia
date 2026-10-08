package dev.axonia.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

import dev.axonia.ClusteredTask;
import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.MigrationChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * What clustered scheduling exists for, on the real job store: schedulers built the way Spring Boot builds the
 * application's, with the Quartz properties of {@code axonia-core.properties}, against a database the Self-hosted chain
 * migrated, connected as {@code axonia_app}. No Spring context, so each scheduler here is one instance of the cluster.
 * They share one pool: Quartz keys its connection providers by scheduler name across the whole JVM, so same-named
 * schedulers in one JVM reach the last pool registered whatever pool each was given.
 */
class ClusteredSchedulingIntegrationTest {

    private static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    private static final String[] QUARTZ_TABLES = {"qrtz_fired_triggers", "qrtz_simple_triggers", "qrtz_cron_triggers",
            "qrtz_simprop_triggers", "qrtz_blob_triggers", "qrtz_triggers", "qrtz_job_details", "qrtz_calendars",
            "qrtz_paused_trigger_grps", "qrtz_scheduler_state"};

    private final List<SchedulerFactoryBean> factories = new ArrayList<>();

    private HikariDataSource pool;

    private final Map<Date, Integer> slotsRun = new ConcurrentHashMap<>();

    @BeforeAll
    static void migrate() {

        PG.start();
        MigrationChain.SELF_HOSTED.on(PG).migrate();
    }

    @AfterEach
    void stopAndClean() throws SQLException, SchedulerException {

        for (var factory : factories) {
            factory.destroy();
        }
        factories.clear();
        if (pool != null) {
            pool.close();
            pool = null;
        }
        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.createStatement()) {
            for (var table : QUARTZ_TABLES) {
                statement.execute("DELETE FROM scheduler." + table);
            }
        }
    }

    @Test
    void twoSchedulersFireOneSlotOnce() throws Exception {

        var runs = new AtomicInteger();
        var task = new ClusteredTask("billing-sweep", new ClusteredTask.Every(Duration.ofSeconds(1), Duration.ZERO),
                runs::incrementAndGet);
        var a = clusteredScheduler("a", task);
        var b = clusteredScheduler("b", task);
        register(a, task);
        register(b, task);

        a.start();
        b.start();
        await().atMost(Duration.ofSeconds(30)).until(() -> runs.get() >= 5);
        a.standby();
        b.standby();

        assertThat(slotsRun).isNotEmpty()
                .allSatisfy((slot, count) -> assertThat(count).as("runs of the slot at %s", slot).isOne());
    }

    @Test
    void aLongTaskNeverOverlapsItselfAcrossInstances() throws Exception {

        var inFlight = new AtomicInteger();
        var mostAtOnce = new AtomicInteger();
        var runs = new AtomicInteger();
        var task = new ClusteredTask("billing-collection",
                new ClusteredTask.Every(Duration.ofMillis(300), Duration.ZERO), () -> {
                    mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    sleep(Duration.ofMillis(900));
                    inFlight.decrementAndGet();
                    runs.incrementAndGet();
                });
        var a = clusteredScheduler("a", task);
        var b = clusteredScheduler("b", task);
        register(a, task);

        a.start();
        b.start();
        await().atMost(Duration.ofSeconds(30)).until(() -> runs.get() >= 4);
        a.standby();
        b.standby();

        assertThat(mostAtOnce).hasValue(1);
    }

    @Test
    void aRestartKeepsTheStoredNextFireTime() throws Exception {

        var task = new ClusteredTask("billing-collection",
                new ClusteredTask.Every(Duration.ofHours(24), Duration.ofHours(24)), () -> {
                });
        var first = clusteredScheduler("a", task);
        register(first, task);
        var stored = nextFireTime(first, task);
        first.shutdown(true);
        Thread.sleep(50);

        var second = clusteredScheduler("b", task);
        register(second, task);

        assertThat(nextFireTime(second, task)).isEqualTo(stored);
    }

    @Test
    void aMissedSlotFiresOnceOnRecovery() throws Exception {

        var runs = new AtomicInteger();
        var task = new ClusteredTask("standing-renewal", new ClusteredTask.Every(Duration.ofMinutes(1), Duration.ZERO),
                runs::incrementAndGet);
        var scheduler = clusteredScheduler("a", task);
        register(scheduler, task);
        backdateNextFireTime(task, Duration.ofMinutes(10));

        scheduler.start();
        await().atMost(Duration.ofSeconds(30)).until(() -> runs.get() >= 1);
        Thread.sleep(3000);

        assertThat(runs).hasValue(1);
    }

    @Test
    void instancesStartingTogetherRegisterTheSameTasksWithoutFailing() throws Exception {

        var tasks = List.of(
                new ClusteredTask("billing-sweep", new ClusteredTask.Every(Duration.ofHours(1), Duration.ZERO), () -> {
                }), new ClusteredTask("billing-collection",
                        new ClusteredTask.Every(Duration.ofHours(24), Duration.ofHours(24)), () -> {
                        }),
                new ClusteredTask("standing-renewal", new ClusteredTask.Cron("0 20 3 * * ?"), () -> {
                }), new ClusteredTask("sso-watch", new ClusteredTask.Every(Duration.ofHours(24), Duration.ZERO), () -> {
                }));
        for (var round = 0; round < 10; round++) {
            var a = clusteredScheduler("a");
            var b = clusteredScheduler("b");
            var together = new CyclicBarrier(2);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> registerAfter(together, a, tasks));
                var second = executor.submit(() -> registerAfter(together, b, tasks));
                assertThat(Arrays.asList(first.get(), second.get())).as("round %d", round).containsOnlyNulls();
            }
            stopAndClean();
        }
    }

    @Test
    void aTriggerLeftInErrorRunsAgainAfterARestart() throws Exception {

        var task = new ClusteredTask("standing-renewal",
                new ClusteredTask.Every(Duration.ofHours(24), Duration.ofHours(24)), () -> {
                });
        var first = clusteredScheduler("a", task);
        register(first, task);
        setTriggerState(task, "ERROR");
        first.shutdown(true);

        var second = clusteredScheduler("b", task);
        register(second, task);

        assertThat(second.getTriggerState(TriggerKey.triggerKey(task.name(), ClusteredTaskJob.TASK_GROUP)))
                .isEqualTo(Trigger.TriggerState.NORMAL);
    }

    private Throwable registerAfter(CyclicBarrier together, Scheduler scheduler, List<ClusteredTask> tasks) {

        try {
            together.await();
            register(scheduler, tasks);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private static void setTriggerState(ClusteredTask task, String state) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.prepareStatement(
                        "UPDATE scheduler.qrtz_triggers SET trigger_state = ? WHERE trigger_name = ?")) {
            statement.setString(1, state);
            statement.setString(2, task.name());
            assertThat(statement.executeUpdate()).isOne();
        }
    }

    private static void sleep(Duration duration) {

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Scheduler clusteredScheduler(String instanceId, ClusteredTask... tasks) throws Exception {

        if (pool == null) {
            pool = new HikariDataSource();
            pool.setJdbcUrl(PG.getJdbcUrl());
            pool.setUsername("axonia_app");
            pool.setPassword("axonia_app");
            pool.setMaximumPoolSize(12);
        }

        var properties = quartzPropertiesOfTheApplication();
        properties.setProperty("org.quartz.scheduler.instanceId", instanceId);
        properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "1000");
        var job = new ClusteredTaskJob(SchedulerConfig.byName(List.of(tasks)));

        var factory = new SchedulerFactoryBean();
        factory.setSchedulerName("axonia");
        factory.setDataSource(pool);
        factory.setQuartzProperties(properties);
        factory.setAutoStartup(false);
        factory.setJobFactory((bundle, scheduler) -> recording(job));
        factory.afterPropertiesSet();
        factories.add(factory);
        return factory.getObject();
    }

    private Job recording(ClusteredTaskJob job) {

        return context -> {
            slotsRun.merge(context.getScheduledFireTime(), 1, Integer::sum);
            job.execute(context);
        };
    }

    private static Properties quartzPropertiesOfTheApplication() throws IOException {

        var application = new Properties();
        try (InputStream in = ClusteredSchedulingIntegrationTest.class.getResourceAsStream("/axonia-core.properties")) {
            application.load(in);
        }
        var quartz = new Properties();
        var prefix = "spring.quartz.properties.";
        application.stringPropertyNames().stream().filter(key -> key.startsWith(prefix))
                .forEach(key -> quartz.setProperty(key.substring(prefix.length()), application.getProperty(key)));
        assertThat(quartz.getProperty("org.quartz.jobStore.isClustered")).isEqualTo("true");
        return quartz;
    }

    private void register(Scheduler scheduler, ClusteredTask task) {

        register(scheduler, List.of(task));
    }

    private void register(Scheduler scheduler, List<ClusteredTask> tasks) {

        var transactions = new TransactionTemplate(new JdbcTransactionManager(pool));
        new ClusteredTaskRegistrar(scheduler, tasks, Clock.systemUTC(), transactions).register();
    }

    private static Date nextFireTime(Scheduler scheduler, ClusteredTask task) throws SchedulerException {

        return scheduler.getTrigger(TriggerKey.triggerKey(task.name(), ClusteredTaskJob.TASK_GROUP)).getNextFireTime();
    }

    private static void backdateNextFireTime(ClusteredTask task, Duration ago) throws SQLException {

        try (var connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var statement = connection.prepareStatement(
                        "UPDATE scheduler.qrtz_triggers SET next_fire_time = ? WHERE trigger_name = ?")) {
            statement.setLong(1, System.currentTimeMillis() - ago.toMillis());
            statement.setString(2, task.name());
            assertThat(statement.executeUpdate()).isOne();
        }
    }
}
