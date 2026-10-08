package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.OrganizationIds;
import dev.axonia.SweepHeartbeat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The drain budget belongs to the tick, not to whichever organization happens to be draining, and the tenant order
 * rotates so no organization is permanently last.
 *
 * <p>
 * Both defects are only observable under real time pressure, so this class drives a {@link ManualClock} it fully
 * controls rather than the wall clock: {@link #outbox}'s mocked {@code claim} advances it by a fixed, known amount per
 * batch claimed, which is what lets the two tests below compute an EXACT expected row count instead of racing real
 * database throughput - the kind of assertion that would be flaky by construction against a real clock. {@code
 * axonia.communication.sweep.enabled=false} keeps the real {@link CommunicationSweep} bean out of every test context
 * (see that class's own javadoc for why), so this class builds one directly from real {@link SweepUnit} and
 * {@link OutboxDrain} instances - the exact wiring a production tick uses - over {@link JdbcOutbox},
 * {@link DrainWorkers}, {@link MailDelivery}, {@link SendPacing} and {@link OrganizationIds} test doubles, and calls
 * {@code drainTick()} by hand rather than waiting on any schedule.
 *
 * <p>
 * {@code BATCH_SIZE} and {@code DRAIN_BUDGET} are chosen so that exactly {@link #BATCHES_PER_BUDGET} batches (250 rows)
 * fit inside one shared deadline, with the last claim landing exactly on it - matching {@link OutboxDrain}'s own
 * "checked only between batches" contract, where the deadline is read before a claim, never during one. Each seeded
 * organization's own backlog (10,000 rows) is chosen to be far larger than what any single tick could ever reach, so
 * neither test can pass merely because a queue happened to run dry.
 */
@ExtendWith(MockitoExtension.class)
class DrainFairnessTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final int BATCH_SIZE = 50;
    private static final Duration DRAIN_BUDGET = Duration.ofSeconds(100);
    private static final Duration BATCH_COST = Duration.ofSeconds(20);
    private static final int BATCHES_PER_BUDGET = 5;
    private static final int SENT_PER_BUDGET = BATCHES_PER_BUDGET * BATCH_SIZE;
    private static final int ROWS_PER_ORG = 10_000;

    @Mock
    private JdbcOutbox outbox;
    @Mock
    private DrainWorkers workers;
    @Mock
    private MailDelivery delivery;
    @Mock
    private SendPacing pacing;
    @Mock
    private OutboxRetention retention;
    @Mock
    private OrganizationIds organizationIds;

    private final Map<String, AtomicInteger> remaining = new HashMap<>();
    private final Map<String, AtomicInteger> sent = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    private ManualClock clock;
    private CommunicationSweep sweep;

    @BeforeEach
    void wireTheRealDrainPath() {

        clock = new ManualClock(NOW);
        var properties = new CommunicationProperties(BATCH_SIZE, DRAIN_BUDGET, 10, 4, 5, Duration.ofMinutes(5),
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(30), Duration.ofDays(90), 200,
                "test-unsubscribe-secret");
        var drain = new OutboxDrain(outbox, workers, delivery, clock, properties, pacing);
        var unit = new SweepUnit(List.of(), drain, clock, retention, properties);
        sweep = new CommunicationSweep(organizationIds, unit, new SweepHeartbeat(new SimpleMeterRegistry(), clock),
                clock, properties);

        lenient().when(outbox.claim(any(JdbcOutbox.ClaimQuery.class))).thenAnswer(invocation -> claimNextBatch());
        lenient().when(workers.runAll(any(), any(), any(), any())).thenAnswer(invocation -> recordSent(invocation));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    /**
     * Stands in for a real claim: pops up to {@code batchSize} rows from whichever organization the calling thread is
     * currently scoped to (read off the real {@code SecurityContext} {@code OrganizationScope.runAs} sets, exactly as
     * the real {@code CLAIM_SQL}'s RLS predicate would), and advances the clock by {@link #BATCH_COST} - simulating
     * this batch's own real-world cost - before returning. This is the ONLY thing in this class that moves the clock:
     * every other collaborator is instant, so the elapsed time between any two points is exactly
     * {@code BATCH_COST * (batches claimed)}, never a stray incidental read.
     */
    private List<OutboxEntity> claimNextBatch() {

        var organizationId = currentScope();
        var left = remaining.computeIfAbsent(organizationId, id -> new AtomicInteger());
        var claimed = Math.min(BATCH_SIZE, Math.max(left.get(), 0));
        if (claimed == 0) {
            return List.of();
        }
        left.addAndGet(-claimed);
        clock.advance(BATCH_COST);
        return IntStream.range(0, claimed).mapToObj(i -> OutboxEntity.builder().id(idGenerator.incrementAndGet())
                .organizationId(organizationId).status("SENDING").build()).toList();
    }

    /**
     * Every row {@link DrainWorkers} is handed comes back {@code PROCESSED}, credited to the claim's own organization.
     */
    private List<DrainWorkers.Outcome> recordSent(InvocationOnMock invocation) {

        String organizationId = invocation.getArgument(0);
        List<?> rows = invocation.getArgument(1);
        sent.computeIfAbsent(organizationId, id -> new AtomicInteger()).addAndGet(rows.size());
        return rows.stream().map(row -> DrainWorkers.Outcome.PROCESSED).toList();
    }

    private void seedOutboxRows(String organizationId, int count) {

        remaining.put(organizationId, new AtomicInteger(count));
    }

    private int sentFor(String organizationId) {

        return sent.getOrDefault(organizationId, new AtomicInteger()).get();
    }

    private static String currentScope() {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        return ((OrganizationBoundAuthentication) auth).organizationId();
    }

    /**
     * The deadline belongs to the whole tick, not to whichever organization happens to be draining: two organizations
     * both far too backlogged for one drain budget to ever exhaust must still cost the tick only one budget's worth of
     * batches combined, not one budget EACH.
     *
     * <p>
     * Before this fix, {@link OutboxDrain#drain} computed a fresh {@code clock.instant().plus(drainBudget)} on every
     * call, so org-a and org-b would each independently claim {@link #BATCHES_PER_BUDGET} batches - a combined total of
     * {@code 2 x SENT_PER_BUDGET} - because org-b's own deadline was computed only after org-a's call returned, from
     * whatever the clock read at that later point, rather than from the tick's start. Sharing one deadline, computed
     * once and handed down, means org-a's own share can still spend the whole of it, but org-b's call finds that SAME
     * deadline already behind the clock and claims nothing more - so the combined total stays at exactly one budget's
     * worth.
     */
    @Test
    void oneBackloggedTenantDoesNotConsumeTheWholeTick() {

        seedOutboxRows("org-a", ROWS_PER_ORG);
        seedOutboxRows("org-b", ROWS_PER_ORG);
        when(organizationIds.all()).thenReturn(List.of("org-a", "org-b"));

        sweep.drainTick();

        assertThat(sentFor("org-a") + sentFor("org-b"))
                .as("one shared deadline for the whole tick, not a fresh one per organization, so two backlogged "
                        + "tenants together still cost only one drain budget's worth of batches")
                .isEqualTo(SENT_PER_BUDGET);
    }

    /**
     * The next tick starts where the last one stopped: {@link CommunicationSweep} rotates which organization goes
     * first, so the organization a heavy neighbour crowded out of one tick is the one to go first - and therefore
     * actually get served - on the next.
     *
     * <p>
     * Both organizations are backlogged heavily enough that neither tick can ever reach the end of either queue, so the
     * only way org-b is EVER served here is by going first. The first tick (no rotation has happened yet) starts in
     * {@code OrganizationIds.all()}'s own {@code ORDER BY id} order - org-a, then org-b - so org-a spends the whole
     * shared deadline and org-b is served nothing. The second tick, having rotated to start right after org-a, gives
     * org-b the turn org-a took last time.
     */
    @Test
    void theNextTickStartsWhereTheLastOneStopped() {

        seedOutboxRows("org-a", ROWS_PER_ORG);
        seedOutboxRows("org-b", ROWS_PER_ORG);
        when(organizationIds.all()).thenReturn(List.of("org-a", "org-b"));

        sweep.drainTick();
        var afterFirst = sentFor("org-b");

        sweep.drainTick();

        assertThat(sentFor("org-b")).as("rotation, so no tenant is permanently last").isGreaterThan(afterFirst);
    }

    /**
     * Advances only when explicitly told to, unlike the wall clock: every {@code instant()} read in between is free, so
     * the only thing that costs simulated time in this class is {@link #claimNextBatch()}'s own explicit
     * {@link #advance}, never an incidental read from deep inside {@link OutboxDrain} or {@link SweepHeartbeat}.
     */
    private static final class ManualClock extends Clock {

        private Instant current;

        ManualClock(Instant start) {

            this.current = start;
        }

        void advance(Duration duration) {

            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {

            return current;
        }
    }
}
