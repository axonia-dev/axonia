package dev.axonia.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import dev.axonia.ClockTicker;

/**
 * The caches behind {@code @Cacheable}. Each is registered here by name, with its own lifetime and bound, and none is
 * created on demand: a name nobody registered resolves to no cache, and the call naming it fails with Spring's "Cannot
 * find cache named" rather than quietly caching with no bound. The names are constants so a caller cannot misspell one.
 *
 * <p>
 * Every cache reads time through {@link ClockTicker}, so a test stepping the application {@link Clock} expires entries
 * exactly as it moves every other deadline, and runs its maintenance on a calling thread rather than a pool, so the
 * bound is restored before the call that drains returns and is exceeded only briefly under concurrent writes. Null
 * values are refused: a cached method answers something or throws, and a thrown read is never stored.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** Microsoft Graph's groups for one Entra tenant, keyed by {@code aadTenantId}. */
    public static final String ENTRA_GROUPS = "entra-groups";

    static final Duration ENTRA_GROUPS_LIFETIME = Duration.ofMinutes(5);

    /** One entry per proved Entra tenant read within its lifetime; a whole directory's group list per entry. */
    static final int ENTRA_GROUPS_MAXIMUM = 256;

    @Bean
    CacheManager cacheManager(Clock clock) {
        var manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        manager.setCacheNames(List.of());
        manager.registerCustomCache(ENTRA_GROUPS,
                Caffeine.newBuilder().ticker(new ClockTicker(clock)).executor(Runnable::run)
                        .expireAfterWrite(ENTRA_GROUPS_LIFETIME).maximumSize(ENTRA_GROUPS_MAXIMUM).build());
        return manager;
    }
}
