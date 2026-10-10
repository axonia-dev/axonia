package dev.axonia.shared.configuration;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which {@code RateLimitStore} backs the application, bound as an enum rather than read as a raw string so that an
 * unrecognised value fails at binding time, during startup - {@link RateLimitConfig#rateLimitStore} resolves this
 * first, so the failure is this one, naming the property, the value and the allowed choices, rather than an opaque "no
 * qualifying bean" once {@code RateLimiters} tries to autowire a store nothing produced.
 *
 * <p>
 * {@link RateLimitStoreConverter} is what makes the failure name the allowed choices: the JDK's own
 * {@code Enum.valueOf} failure, which {@link org.springframework.boot.convert.LenientObjectToEnumConverterFactory}
 * would otherwise propagate unchanged, names only the class and the rejected value.
 */
@ConfigurationProperties(prefix = "axonia.rate-limit")
record RateLimitProperties(@DefaultValue("REDIS") Store store) {

    enum Store {
        MEMORY, REDIS
    }

    static Store parse(String value) {

        try {
            return Store.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("axonia.rate-limit.store must be one of %s, got '%s'"
                    .formatted(Arrays.toString(Store.values()), value), e);
        }
    }
}
