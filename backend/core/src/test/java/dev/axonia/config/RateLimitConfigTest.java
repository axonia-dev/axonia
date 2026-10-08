package dev.axonia.config;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.axonia.RateLimitStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfig.class).withBean(Clock.class, Clock::systemUTC)
            .withBean(SimpleMeterRegistry.class);

    /**
     * No {@code StringRedisTemplate} or {@code DataRedisProperties} bean is registered at all here - the point of this
     * test, alongside {@link RateLimitConfig}'s own Javadoc: {@code memory} must not need either.
     */
    @Test
    void selectsTheInMemoryStoreWhenAskedTo() {

        runner.withPropertyValues("axonia.rate-limit.store=memory")
                .run(context -> assertThat(context.getBean(RateLimitStore.class))
                        .isInstanceOf(InMemoryRateLimitStore.class));
    }

    @Test
    void defaultsToTheRedisStoreWhenNothingIsConfigured() {

        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(DataRedisProperties.class, () -> mock(DataRedisProperties.class))
                .run(context -> assertThat(context.getBean(RateLimitStore.class))
                        .isInstanceOf(RedisRateLimitStore.class));
    }

    @Test
    void selectsTheRedisStoreWhenAskedToExplicitly() {

        runner.withPropertyValues("axonia.rate-limit.store=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(DataRedisProperties.class, () -> mock(DataRedisProperties.class))
                .run(context -> assertThat(context.getBean(RateLimitStore.class))
                        .isInstanceOf(RedisRateLimitStore.class));
    }

    /**
     * Asserts the converter's own full text, not only the substrings Spring's own wrapping exceptions already print
     * regardless of what any converter says - {@code "axonia.rate-limit.store"} and {@code "bogus"} both appear in
     * {@code BindException}'s message on any binding failure, converter or not, so checking only those would pass even
     * if {@link RateLimitStoreConverter} were never consulted at all.
     */
    @Test
    void refusesAnUnknownStoreChoiceAtStartupWithAClearMessage() {

        runner.withPropertyValues("axonia.rate-limit.store=bogus").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("axonia.rate-limit.store must be one of [MEMORY, REDIS], got 'bogus'");
        });
    }
}
