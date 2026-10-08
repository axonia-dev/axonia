package dev.axonia;

import java.time.Clock;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * A "has this been done lately" memory, so a per-request side effect costs one write per key per window rather than one
 * per request.
 *
 * <p>
 * The check and the mark are two calls rather than one on purpose. Marking on the check would record work that then
 * failed, silently skipping it for the rest of the window; callers mark only after the work they were debouncing has
 * actually happened.
 *
 * <p>
 * {@link #claim} is the one-call form, for work that must not be started twice in a window even by requests racing each
 * other, and whose caller accepts that a failure is not retried until the window has passed. It is a
 * {@code putIfAbsent}, not a {@code compute}: Caffeine restarts an entry's write time on any {@code compute} that
 * returns a value, so a losing claim written that way would push the window forward and a busy key would never fall due
 * again.
 *
 * <p>
 * A Caffeine cache. A key is due again one window after it was marked, measured on the application's {@link Clock}
 * through {@link ClockTicker}. At its cap the cache evicts single keys by its own size policy, so a full memory costs
 * one extra write for the key it dropped rather than making every key due at once. Its maintenance runs on a calling
 * thread rather than a pool, so the cap is restored before the call that drains returns, and concurrent writes can
 * exceed it only briefly.
 *
 * <p>
 * Per instance and per process: it debounces, it does not coordinate. Two application instances each keep their own
 * memory, and within one instance the check and the mark are not atomic, so concurrent requests can all pass
 * {@link #isDue} together. Every caller therefore has to be safe to run twice - which is why the writes it guards are
 * an upsert, a targeted update, and an insert whose caller catches the duplicate key.
 *
 * <p>
 * A kernel type because more than one module keeps a memory of this shape. A second copy of a debounce whose two-call
 * contract is this easy to get wrong is not worth the packaging.
 */
public class RecentlySeen {

    private final Cache<String, Boolean> seen;

    public RecentlySeen(Duration window, int maxSize, Clock clock) {

        this.seen = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(maxSize).ticker(new ClockTicker(clock))
                .executor(Runnable::run).build();
    }

    public boolean isDue(String key) {

        return seen.getIfPresent(key) == null;
    }

    /**
     * Marks the key as done now if it is due, atomically, and says whether this call did. Of several calls racing on
     * one due key, one wins, and the others leave its window where the winner started it.
     */
    public boolean claim(String key) {

        return seen.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    public void remember(String key) {

        seen.put(key, Boolean.TRUE);
    }
}
