package dev.axonia;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Wraps a scheduled tick so that its silence becomes visible.
 *
 * <p>
 * Ticks run in production on cadences from 60 seconds to daily, and none of them announced anything before this
 * existed: a scheduler that stops firing produces no error, no log line, and no missing row anyone would look for.
 *
 * <p>
 * Every meter for every sweep is registered at construction rather than on that sweep's first run. A gauge that only
 * appears once a sweep has succeeded emits no series at all for a sweep that has never succeeded, so an alert on the
 * gap between now and that gauge matches nothing and stays quiet for the one failure the metric exists to catch: a tick
 * broken since the deploy. Each gauge is seeded with the instant this process started rather than with zero, so the gap
 * reads "has not succeeded since this process started". That is correct both ways round - a sweep broken since boot
 * alerts once its own threshold passes, and a daily sweep whose first run is still ahead of it stays under that
 * threshold instead of paging on every redeploy.
 *
 * <p>
 * Only a run that completed advances a last-success gauge. A gauge refreshed by a failing run would report health while
 * the job was broken, which is worse than no gauge at all. The alert rules read the gap between now and this gauge per
 * sweep, because a 60 second drain and a daily retention are not the same alert.
 *
 * <p>
 * It rethrows, and what reaches that rethrow is a tick's own discovery read - {@code unit.today()}. Each tick's
 * per-unit try/catch sits inside the {@link Runnable} and by construction cannot catch what escapes it. Such an
 * exception goes on escaping {@code tick()} to Spring's scheduled-task error handler exactly as it did before this
 * class existed, which is the point: swallowing it here would silently change what an aborted run does.
 *
 * <p>
 * This is a deadman, and it answers one question: is the scheduler running at all. It is not a health signal. Every
 * tick wraps each unit of work in its own try/catch and swallows that unit's failure into a {@code log.warn}. The unit
 * is an organization for most of them, and a person for a sweep that runs per person. A run in which every unit failed
 * therefore still reaches the end of the {@link Runnable} and still counts as a success here. Per-unit failure rates
 * are a separate measurement.
 *
 * <p>
 * The names are a contract the alert rules read literally, which is why {@link Sweep} is a type rather than a string:
 * the open sweeps are its constants, a module running a sweep of its own contributes it as a {@link MeterContribution},
 * and every one is registered at construction, so a name nothing registered fails at the call instead of writing a
 * series nobody reads.
 *
 * <p>
 * Lives in the root package rather than {@code config} because several modules call it, and Spring Modulith treats
 * every direct subpackage of {@code dev.axonia} as a module.
 */
@Component
public class SweepHeartbeat {

    private static final String RUNS = "axonia.sweep.runs";

    private static final String LAST_SUCCESS = "axonia.sweep.last.success.seconds";

    private final Clock clock;

    private final Map<Sweep, SweepMeters> meters;

    public SweepHeartbeat(MeterRegistry registry, Clock clock) {

        this(registry, clock, List.of());
    }

    @Autowired
    public SweepHeartbeat(MeterRegistry registry, Clock clock, List<MeterContribution> contributions) {

        this.clock = clock;
        var startedAt = clock.instant().getEpochSecond();
        this.meters = MeterVocabulary
                .join(Sweep.open(), contributions.stream().flatMap(contribution -> contribution.sweeps().stream()),
                        Sweep::tag)
                .stream().collect(Collectors.toUnmodifiableMap(Function.identity(),
                        sweep -> new SweepMeters(registry, sweep, startedAt)));
    }

    public void record(Sweep sweep, Runnable tick) {

        var watched = MeterVocabulary.registered(meters, sweep, sweep.tag());
        try {
            tick.run();
        } catch (RuntimeException e) {
            watched.failures.increment();
            throw e;
        }
        watched.successes.increment();
        watched.lastSuccess.set(clock.instant().getEpochSecond());
    }

    /** A scheduled tick, carrying the {@code sweep} tag its own alert rule matches on. */
    public record Sweep(String tag) {

        public static final Sweep COMMUNICATION_PRODUCE = new Sweep("communication-produce");

        public static final Sweep COMMUNICATION_DRAIN = new Sweep("communication-drain");

        public static final Sweep COMMUNICATION_RETENTION = new Sweep("communication-retention");

        public static final Sweep AGENT_CONNECTION_RETENTION = new Sweep("agent-connection-retention");

        public Sweep {

            MeterVocabulary.requireTag(tag);
        }

        static List<Sweep> open() {

            return List.of(COMMUNICATION_PRODUCE, COMMUNICATION_DRAIN, COMMUNICATION_RETENTION,
                    AGENT_CONNECTION_RETENTION);
        }
    }

    /**
     * Holds one sweep's two counters and the {@link AtomicLong} its gauge reads, rather than the registry they came
     * from, following {@code RateLimiter}: meters resolved once are what the hot path wants anyway, and it keeps the
     * injected {@link MeterRegistry} out of any field. The {@link AtomicLong} field is load-bearing rather than
     * convenient - Micrometer holds a gauge's state object weakly, so the gauge stops reporting as soon as nothing else
     * references it.
     */
    private static final class SweepMeters {

        private final Counter successes;

        private final Counter failures;

        private final AtomicLong lastSuccess;

        private SweepMeters(MeterRegistry registry, Sweep sweep, long startedAt) {

            this.successes = Counter.builder(RUNS).tag("sweep", sweep.tag()).tag("outcome", "success")
                    .register(registry);
            this.failures = Counter.builder(RUNS).tag("sweep", sweep.tag()).tag("outcome", "failure")
                    .register(registry);
            this.lastSuccess = new AtomicLong(startedAt);
            Gauge.builder(LAST_SUCCESS, this.lastSuccess, AtomicLong::doubleValue).tag("sweep", sweep.tag())
                    .register(registry);
        }
    }
}
