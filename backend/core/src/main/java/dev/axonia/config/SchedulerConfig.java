package dev.axonia.config;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.quartz.autoconfigure.QuartzDataSource;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

import dev.axonia.ClusteredTask;

/**
 * Quartz's clustered job store and the {@link ClusteredTask} beans it runs.
 *
 * <p>
 * Quartz gets its own small pool as {@code axonia_app}, never {@code OrganizationAwareDataSource}: its tables hold no
 * tenant data, and its row locks must never wait behind request handling. It gets its own transaction manager too,
 * because Boot otherwise hands it the primary one, which manages a different pool. That one is not a bean: a second
 * transaction manager bean would stop Boot creating the application's own.
 *
 * <p>
 * Registration runs only where the scheduler starts. A test context never starts it, and test contexts share one
 * database, so one that registered its tasks would delete every other context's.
 */
@Configuration(proxyBeanMethods = false)
public class SchedulerConfig {

    @Bean
    @ConfigurationProperties(prefix = "axonia.scheduler.datasource")
    DataSourceProperties schedulerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @QuartzDataSource
    @ConfigurationProperties(prefix = "axonia.scheduler.datasource.hikari")
    HikariDataSource schedulerDataSource(@Qualifier("schedulerDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    SchedulerFactoryBeanCustomizer schedulerTransactions(@Qualifier("schedulerDataSource") DataSource dataSource) {
        return factory -> factory.setTransactionManager(new JdbcTransactionManager(dataSource));
    }

    @Bean
    Map<String, ClusteredTask> clusteredTasks(List<ClusteredTask> tasks) {
        return byName(tasks);
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "spring.quartz.auto-startup", matchIfMissing = true)
    ApplicationRunner clusteredTaskRegistration(Scheduler scheduler, List<ClusteredTask> tasks, Clock clock,
            @Qualifier("schedulerDataSource") DataSource dataSource) {
        var transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        return arguments -> new ClusteredTaskRegistrar(scheduler, tasks, clock, transactions).register();
    }

    static Map<String, ClusteredTask> byName(List<ClusteredTask> tasks) {

        return tasks.stream()
                .collect(Collectors.toUnmodifiableMap(ClusteredTask::name, Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Two clustered tasks are named " + first.name());
                }));
    }
}
