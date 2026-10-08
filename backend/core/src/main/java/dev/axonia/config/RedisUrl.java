package dev.axonia.config;

/**
 * Refuses a Redis URL that is still an unresolved configuration placeholder.
 *
 * <p>
 * The trap: the configuration binder does not fail on an unresolved {@code ${...}}, it binds the literal text of the
 * placeholder as the value. For most settings that produces a loud failure soon afterwards. Here it would not: the rate
 * limiter fails open by design, so a PROD deployment with {@code REDIS_URL} unset would boot cleanly, dial a host named
 * after the placeholder, fail every call, allow every request, and report itself healthy while enforcing nothing. This
 * is the one configuration mistake that fail-open turns from an outage into a silent absence of the guard rail, so it
 * is checked at boot.
 */
final class RedisUrl {

    private RedisUrl() {
    }

    static String validated(String url) {

        if (url != null && url.contains("${")) {
            throw new IllegalStateException(
                    "spring.data.redis.url is an unresolved property placeholder (%s); set REDIS_URL on the deployment"
                            .formatted(url));
        }
        return url;
    }
}
