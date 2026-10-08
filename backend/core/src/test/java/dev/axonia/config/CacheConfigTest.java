package dev.axonia.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import com.github.benmanes.caffeine.cache.Cache;

import dev.axonia.SteppableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheConfigTest {

    private final SteppableClock clock = new SteppableClock(Instant.parse("2020-01-01T00:00:00Z"));

    private final CacheManager manager = new CacheConfig().cacheManager(clock);

    @Test
    void registersTheEntraGroupsCacheAndNothingElse() {

        assertThat(manager.getCacheNames()).containsExactly(CacheConfig.ENTRA_GROUPS);
    }

    @Test
    void aNameNobodyRegisteredResolvesToNoCacheRatherThanAnUnboundedOne() {

        assertThat(manager.getCache("entra-grups")).isNull();
        assertThat(manager.getCacheNames()).containsExactly(CacheConfig.ENTRA_GROUPS);
    }

    @Test
    void theEntraGroupsCacheIsBoundedAndLivesFiveMinutesFromWrite() {

        var nativeCache = nativeEntraGroups();

        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(256L);
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void anEntryExpiresOnTheApplicationClock() {

        // given
        var cache = manager.getCache(CacheConfig.ENTRA_GROUPS);
        cache.put("tenant-acme", List.of());

        // when / then
        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertThat(cache.get("tenant-acme")).isNotNull();
        clock.advance(Duration.ofNanos(1));
        assertThat(cache.get("tenant-acme")).isNull();
    }

    @Test
    void neverHoldsMoreGroupListsThanItsBound() {

        // given
        var cache = manager.getCache(CacheConfig.ENTRA_GROUPS);

        // when
        for (var i = 0; i <= CacheConfig.ENTRA_GROUPS_MAXIMUM; i++) {
            cache.put("tenant-" + i, List.of());
        }

        // then
        assertThat(nativeEntraGroups().estimatedSize()).isLessThanOrEqualTo(CacheConfig.ENTRA_GROUPS_MAXIMUM);
    }

    @Test
    void aNullIsNeverStored() {

        var cache = manager.getCache(CacheConfig.ENTRA_GROUPS);

        assertThatThrownBy(() -> cache.put("tenant-acme", null)).isInstanceOf(IllegalArgumentException.class);
    }

    private Cache<?, ?> nativeEntraGroups() {

        return (Cache<?, ?>) manager.getCache(CacheConfig.ENTRA_GROUPS).getNativeCache();
    }
}
