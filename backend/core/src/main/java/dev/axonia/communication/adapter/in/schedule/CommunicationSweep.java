package dev.axonia.communication.adapter.in.schedule;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.axonia.communication.application.OutboxDrain;
import dev.axonia.communication.application.SweepUnit;
import dev.axonia.communication.configuration.CommunicationProperties;
import dev.axonia.communication.configuration.CommunicationTasks;
import dev.axonia.organization.api.OrganizationIds;
import dev.axonia.shared.api.schedule.SweepHeartbeat;
import dev.axonia.shared.api.schedule.SweepHeartbeat.Sweep;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The entry point of the sweep: for every organization, produce whatever due work exists and drain whatever is now
 * claimable - both inside that organization's own RLS scope. Nothing before this class ever called
 * {@code SessionProducers} or {@code OutboxDrain}; it is the first thing that does.
 *
 * <p>
 * Producing and draining no longer share one tick. Producing is expensive (it walks a community's roster and session
 * history) and rarely urgent (a wrap-up landing a few minutes later than the fastest possible instant is unnoticeable),
 * while draining is cheap (one claimed batch, already-rendered rows) and time-sensitive (a member waiting on a magic
 * link or a wrap-up they were just told about). Tying both to one interval meant every tick paid the expensive half's
 * cost to get the cheap half's latency, or bought the cheap half's latency only by running the expensive half
 * needlessly often. {@link #produceTick} and {@link #drainTick} are therefore two independent ticks on two independent
 * properties: {@code axonia.communication.produce-interval} (default {@code 5m}) and
 * {@code axonia.communication.drain-interval} (default {@code 60s}, the former {@code sweep-interval} renamed to say
 * what it now exclusively governs). They are scheduled differently, too: {@link #drainTick} is {@code @Scheduled} on
 * every instance, because each instance drains the batch it claimed, while {@link #produceTick} and
 * {@link #retentionTick} are {@link CommunicationTasks}' cluster-wide tasks, run once across every instance.
 *
 * <p>
 * Each tick reads every organization id once, through {@link OrganizationIds}' own tiny privileged pool - never through
 * the primary, RLS-scoped {@code axonia_app} connection, which would silently see nothing at this point (there is no
 * organization yet to scope to; discovering them is the point). For each id, {@link OrganizationScope#runAs} wraps the
 * call into {@link SweepUnit#produce} or {@link SweepUnit#drain} from outside: the {@code app.current_org} GUC is
 * chosen each time one of that method's callees opens its own transaction, so a scope entered after that point is
 * silently ignored. See {@link SweepUnit}'s own class javadoc for why neither method carries one {@code @Transactional}
 * of its own spanning the whole tick - production and the drain each manage their own, narrower transactions - for why
 * every one of those still needs to be a call through a real bean reference, never a self-invocation, and for what
 * letting the two schedules overlap in practice relies on. {@link #retentionTick} follows the identical
 * per-organization shape for {@link SweepUnit#retain} (the ninety-day pass), but not the identical delay shape - see
 * the next paragraph. Its interval is {@code axonia.communication.retention-interval}, defaulting to {@code 24h} - a
 * daily pass is ample against a ninety-day window, where {@code JdbcOutboxRetention}'s own javadoc explains why the
 * transaction it opens stays exactly one, not a cheaper split into two.
 *
 * <p>
 * Each organization is wrapped in its own {@code try/catch}, in all three methods: one tenant's failure - a bad
 * template, a transient SMTP outage - must never abort a tick for every other tenant. The failure log carries only the
 * organization id and the failing exception's class name, never anything about a message: no address, no body, no
 * token.
 *
 * <p>
 * {@link #produceTick} and {@link #drainTick} each delay their first run by one full interval rather than firing the
 * instant the scheduler starts: an initial delay equal to the interval means a fresh deploy's very first moments are
 * never spent racing whatever a test class - or a real deploy's own startup - is still seeding. {@link #retentionTick}
 * deliberately breaks that pattern: its initial delay is the separate, short
 * {@code axonia.communication.retention-initial-delay} property (default {@code 10m}), independent of its 24-hour
 * interval, so the first registration of the task reaches its first pass on the day it is deployed rather than a day
 * later; a short initial delay costs nothing against a ninety-day retention window.
 *
 * <p>
 * Neither cluster-wide task ever fires in a test, because the clustered scheduler never starts there.
 * {@link #drainTick} would, though: {@code @EnableScheduling} sits on the application class itself, so every
 * {@code @SpringBootTest} boots a live scheduler, and Spring's test context cache keeps a context - and its scheduler -
 * alive for the whole gate, not just one test class's lifetime. This bean is therefore entirely absent from the context
 * under the test profile ({@code axonia.communication.sweep.enabled=false} in
 * {@code src/test/resources/axonia-core-test.properties}) via the {@code @ConditionalOnProperty} below - not merely
 * delayed - because a tick landing between two statements of an unrelated integration test (seeding, then asserting) is
 * a real, observed failure mode, not a hypothetical one, and the shared {@code jdbc:tc} container's driver connects as
 * its superuser regardless of which pool asks, bypassing RLS entirely for whichever organization the tick finds. The
 * flag stays singular on purpose: {@link CommunicationTasks} carries the same condition, so disabling it removes
 * {@link #produceTick}, {@link #drainTick} and {@link #retentionTick} together, with no second flag to remember.
 * {@code CommunicationSweepDisabledInTestsTest} pins this by asserting the bean's absence; removing the annotation
 * turns that test red.
 */
@Component
@ConditionalOnProperty(prefix = "axonia.communication.sweep", name = "enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CommunicationSweep {

    private final OrganizationIds organizationIds;
    private final SweepUnit unit;
    private final SweepHeartbeat heartbeat;
    private final Clock clock;
    private final CommunicationProperties properties;

    /**
     * The organization {@link #drainTick} started with last time, so the next tick can rotate the list to start right
     * after it - see {@link #drainTick}'s own javadoc for why. {@code volatile} rather than {@code AtomicReference}:
     * every write and read of it happens inside the single, non-reentrant {@code drainTick} invocation the scheduler
     * ever has running at once (see {@link SweepUnit}'s class javadoc on why the two schedules never race each other
     * for the SAME tick), so there is nothing to compare-and-swap - only a plain visibility guarantee between one
     * tick's write and the next tick's read, on whatever thread the scheduler happens to run each on.
     */
    private volatile String lastDrainedOrganizationId;

    public void produceTick() {

        heartbeat.record(Sweep.COMMUNICATION_PRODUCE, () -> {
            for (var organizationId : organizationIds.all()) {
                try {
                    OrganizationScope.runAs(organizationId, () -> unit.produce(organizationId));
                } catch (RuntimeException e) {
                    log.warn("Communication produce failed for organization {}: {}", organizationId,
                            e.getClass().getSimpleName());
                }
            }
        });
    }

    /**
     * The deadline belongs to this tick, not to any one organization: computed once, from the SAME {@link Clock}
     * reading, before the loop even starts, and handed unchanged into every {@link SweepUnit#drain} call.
     * {@link OutboxDrain#drain} used to compute its own fresh deadline on every call, which meant the budget was per
     * organization rather than per tick - two backlogged organizations already overran {@code drain-interval}, and ten
     * made it run seven times as long, silently. Sharing one deadline instead means a heavily backlogged organization
     * can still spend the whole of it, but it can no longer add a full budget of its own on top of every other
     * organization's.
     *
     * <p>
     * That alone would let whichever organization happens to run first always win the tick's budget, and every
     * organization after it starve for as long as the first stays backlogged - {@link OrganizationIds#all}'s own
     * {@code ORDER BY id} gives a stable order, and a stable order with no rotation is a stable loser.
     * {@link #lastDrainedOrganizationId} is what {@link #rotate} uses to start each tick right after wherever the
     * previous one started, so the organization a heavy neighbour crowded out this tick is the one to go first next
     * time rather than the one permanently last.
     */
    @Scheduled(initialDelayString = "${axonia.communication.drain-interval:60s}", fixedDelayString = "${axonia.communication.drain-interval:60s}")
    public void drainTick() {

        heartbeat.record(Sweep.COMMUNICATION_DRAIN, () -> {
            var deadline = clock.instant().plus(properties.drainBudget());
            var order = rotate(organizationIds.all(), lastDrainedOrganizationId);
            for (var organizationId : order) {
                try {
                    OrganizationScope.runAs(organizationId, () -> unit.drain(organizationId, deadline));
                } catch (RuntimeException e) {
                    log.warn("Communication drain failed for organization {}: {}", organizationId,
                            e.getClass().getSimpleName());
                }
            }
            if (!order.isEmpty()) {
                lastDrainedOrganizationId = order.getFirst();
            }
        });
    }

    /**
     * {@code organizationIds} in {@code ORDER BY id} order, cut right after {@code after} and wrapped around, so the
     * returned list starts with whatever comes right after {@code after} and ends with {@code after} itself. {@code
     * after} missing from {@code organizationIds} - the very first tick, ever, when nothing has run yet, or an
     * organization that left the active set between two ticks - leaves the order unchanged rather than guessing; the
     * next tick that finds its {@code lastDrainedOrganizationId} still active resumes rotating from there.
     */
    private static List<String> rotate(List<String> organizationIds, String after) {

        var index = after == null ? -1 : organizationIds.indexOf(after);
        if (index < 0) {
            return organizationIds;
        }
        var rotated = new ArrayList<String>(organizationIds.size());
        rotated.addAll(organizationIds.subList(index + 1, organizationIds.size()));
        rotated.addAll(organizationIds.subList(0, index + 1));
        return rotated;
    }

    public void retentionTick() {

        heartbeat.record(Sweep.COMMUNICATION_RETENTION, () -> {
            for (var organizationId : organizationIds.all()) {
                try {
                    OrganizationScope.runAs(organizationId, () -> unit.retain(organizationId));
                } catch (RuntimeException e) {
                    log.warn("Communication retention failed for organization {}: {}", organizationId,
                            e.getClass().getSimpleName());
                }
            }
        });
    }
}
