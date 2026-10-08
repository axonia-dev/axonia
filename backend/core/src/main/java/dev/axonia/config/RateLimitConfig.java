package dev.axonia.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.axonia.RateLimitStore;
import dev.axonia.RateLimiters;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

    /**
     * {@code static}, and {@code ROLE_INFRASTRUCTURE}, for the same reason {@code McpScannerRolesConfig} marks its own
     * four beans that way: a {@code @ConfigurationPropertiesBinding} converter has to exist before the binder can bind
     * {@link RateLimitProperties}, which happens ahead of the rest of the post-processor chain, so resolving it
     * non-statically would instantiate {@code RateLimitConfig} itself early and trip Spring's
     * {@code BeanPostProcessorChecker} warning for no behavioural reason - nothing here is actually misconfigured, only
     * created earlier than the checker expects. {@code static} keeps this bean from needing the enclosing class
     * instantiated at all, and {@code ROLE_INFRASTRUCTURE} tells the checker this bean is expected to run early.
     */
    @Bean
    @ConfigurationPropertiesBinding
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static RateLimitStoreConverter rateLimitStoreConverter() {

        return new RateLimitStoreConverter();
    }

    /**
     * {@code properties} is resolved first, so an unrecognised {@code axonia.rate-limit.store} fails binding here,
     * during startup, with a message naming the property and the allowed values - not later, as an opaque "no
     * qualifying bean" once {@link RateLimiters} fails to autowire a store nothing produced.
     *
     * <p>
     * The two Redis beans are resolved through {@link ObjectProvider} rather than as ordinary parameters, so choosing
     * {@code memory} never requires a {@code StringRedisTemplate} to exist - a Self-hosted instance needs no Redis
     * connection for its rate limits at all.
     */
    @Bean
    RateLimitStore rateLimitStore(RateLimitProperties properties, Clock clock,
            ObjectProvider<StringRedisTemplate> redis, ObjectProvider<DataRedisProperties> redisProperties) {

        return switch (properties.store()) {
            case MEMORY -> new InMemoryRateLimitStore(clock);
            case REDIS -> {
                RedisUrl.validated(redisProperties.getObject().getUrl());
                yield new RedisRateLimitStore(redis.getObject(), clock);
            }
        };
    }

    @Bean
    RateLimiters rateLimiters(RateLimitStore store, MeterRegistry meters) {

        return new RateLimiters(store, meters);
    }
}
