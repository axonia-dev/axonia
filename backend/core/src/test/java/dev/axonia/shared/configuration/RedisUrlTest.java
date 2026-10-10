package dev.axonia.shared.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisUrlTest {

    @Test
    void rejectsAnUnresolvedPlaceholder() {

        // The binder does NOT fail on an unresolved ${...}, it hands over the placeholder's own text. With
        // fail-open that would boot cleanly, dial a host named "${REDIS_URL}", and allow every request forever while
        // reporting itself healthy.
        assertThatThrownBy(() -> RedisUrl.validated("${REDIS_URL}")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.url");
    }

    @Test
    void acceptsAResolvedUrl() {

        assertThat(RedisUrl.validated("redis://:secret@redis.internal.example:6379"))
                .isEqualTo("redis://:secret@redis.internal.example:6379");
    }

    @Test
    void acceptsNoUrlAtAll() {

        // A profile that configures no Redis is a legitimate state; what must never pass is a URL that looks resolved
        // and is not.
        assertThat(RedisUrl.validated(null)).isNull();
    }
}
