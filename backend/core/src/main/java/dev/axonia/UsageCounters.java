package dev.axonia;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The things worth watching as a business rather than as a system.
 *
 * <p>
 * They land on the same dashboard and the same time axis as the system row, which is the point of having them here at
 * all: a drop in sessions held and a spike in mail failures are usually one incident, and they only look like one when
 * they share an x-axis.
 *
 * <p>
 * Tagged by event and by nothing else. <strong>Never by tenant.</strong> An {@code organization_id} tag would multiply
 * every series by the customer count and exhaust the metrics ceiling, at which point metrics stop arriving with nobody
 * told - the same cardinality trap {@link IntegrationMetrics} names, and the one thing somebody will be tempted to add
 * here. Per-tenant usage is a question for the product's own reporting, which already holds it, at a fidelity no
 * counter could match.
 *
 * <p>
 * Each event is counted at the point the thing actually happened rather than where it was asked for: after the
 * transaction that made it true, or after the send that reached somebody. A counter incremented on an attempt measures
 * traffic, not usage, and would read healthy through a release in which every one of those attempts failed.
 *
 * <p>
 * The names are a contract the dashboards read literally, which is why {@link Event} is a type rather than a string:
 * the open events are its constants, a module counting an event of its own contributes it as a
 * {@link MeterContribution}, and every one is registered at construction, so a name nothing registered fails at the
 * call instead of writing a series nobody reads.
 *
 * <p>
 * Every counter is registered at construction rather than on that event's first occurrence. A counter that springs into
 * existence on first use emits no series at all while nothing is happening, which is exactly when a business metric is
 * being read: "no sign-ups today" and "the sign-up path has been broken since the deploy" must not both look like an
 * empty panel.
 *
 * <p>
 * {@link Event#EMAIL_SENT} deliberately overlaps {@code axonia.integration.calls{integration=smtp}} and neither is
 * redundant. That counter counts round trips to the provider, including the one connection each batch opens before any
 * message rides over it; this one counts messages the transport accepted for delivery, one per member addressed. They
 * answer different questions - "is the provider answering" against "is mail going out to people" - and a release that
 * renders every message empty moves neither, which is why the outcome counters exist alongside both. Neither is a
 * delivery receipt: an asynchronous bounce, a spam folder or a silently discarded recipient all count here, because
 * SMTP tells the sender none of that at the moment of the send.
 *
 * <p>
 * Lives in the root package rather than {@code config} because several modules reach it, and Spring Modulith treats
 * every direct subpackage of {@code dev.axonia} as a module.
 */
@Component
public class UsageCounters {

    private static final String EVENTS = "axonia.usage.events";

    /**
     * Holds the resolved counters rather than the {@link MeterRegistry} they came from, following {@code RateLimiter},
     * {@link SweepHeartbeat} and {@link IntegrationMetrics}: a meter resolved once is what a call site wants anyway,
     * and it keeps the injected registry out of any field, which SpotBugs raises {@code EI_EXPOSE_REP2} on.
     */
    private final Map<Event, Counter> counters;

    public UsageCounters(MeterRegistry registry) {

        this(registry, List.of());
    }

    @Autowired
    public UsageCounters(MeterRegistry registry, List<MeterContribution> contributions) {

        this.counters = MeterVocabulary
                .join(Event.open(), contributions.stream().flatMap(contribution -> contribution.events().stream()),
                        Event::tag)
                .stream().collect(Collectors.toUnmodifiableMap(Function.identity(),
                        event -> Counter.builder(EVENTS).tag("event", event.tag()).register(registry)));
    }

    public void record(Event event) {

        MeterVocabulary.registered(counters, event, event.tag()).increment();
    }

    /**
     * {@link #record} for a call site that sits inside the transaction which makes the event true, deferring the
     * increment until that transaction has actually committed.
     *
     * <p>
     * Counted inline instead, a community created in a transaction that later rolls back would still show on the
     * dashboard, and the release where every one of them rolled back would be the release the panel looked healthiest.
     * The deferral is the same {@code TransactionSynchronizationManager} mechanism {@code RecognitionDataPurger}
     * already uses.
     *
     * <p>
     * With no transaction in flight the increment happens immediately, because there is nothing left to wait for: that
     * is the honest reading for a caller whose write has already committed, and it is also what keeps a unit test of
     * such a caller measuring the same thing production does. The guard is required rather than defensive:
     * {@code registerSynchronization} throws when no synchronization is active.
     *
     * <p>
     * That fallback has one cost worth naming, because no test can catch it: a plain unit test of a transactional
     * service runs with nothing in flight, so it takes the immediate branch and would still pass if
     * {@code @Transactional} were removed from the method it is testing. The counter would then quietly go back to
     * counting the attempt. That is the safe direction for a metric, and the transactional annotations themselves are
     * load-bearing for the writes rather than for this, but it is the reason a green suite is not evidence that a call
     * site is deferred.
     *
     * <p>
     * The event's counter is resolved at the call, so an event nobody contributed fails inside the caller's
     * transaction, as {@link MeterContribution} promises, rather than after its write has committed.
     */
    public void recordAfterCommit(Event event) {

        var counter = MeterVocabulary.registered(counters, event, event.tag());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {

                counter.increment();
            }
        });
    }

    /** A product event, carrying the {@code event} tag its own dashboard panel matches on. */
    public record Event(String tag) {

        /**
         * A person who did not have a profile in this Organization now has one. Counted only where a new
         * {@code identity_user} row is actually created by somebody signing in for the first time - never for a SCIM
         * push, which provisions a whole directory ahead of anybody arriving, and never for the reconciliation that
         * attaches a subject to a row SCIM had already created.
         */
        public static final Event SIGN_UP = new Event("sign-up");

        /**
         * An Organization now exists that did not before, whichever path made it: the operator-driven enterprise
         * provisioning run, and any other path an edition provisions one by. One series covers them all, with no
         * {@code kind} tag separating them - the metric is named for what was provisioned, and splitting it is a
         * cardinality decision for the day somebody actually wants the split.
         */
        public static final Event ORGANIZATION_PROVISIONED = new Event("organization-provisioned");

        public static final Event COMMUNITY_CREATED = new Event("community-created");

        /** An organizer confirming a past session took place. Not a session scheduled, and not one called off. */
        public static final Event SESSION_HELD = new Event("session-held");

        public static final Event EMAIL_SENT = new Event("email-sent");

        public Event {

            MeterVocabulary.requireTag(tag);
        }

        static List<Event> open() {

            return List.of(SIGN_UP, ORGANIZATION_PROVISIONED, COMMUNITY_CREATED, SESSION_HELD, EMAIL_SENT);
        }
    }
}
