package dev.axonia.shared.api.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import dev.axonia.shared.api.metrics.UsageCounters.Event;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageCountersTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final UsageCounters counters = new UsageCounters(registry);

    @Test
    void countsAnEventByName() {

        counters.record(Event.SIGN_UP);
        counters.record(Event.SIGN_UP);

        assertThat(events("sign-up")).isEqualTo(2.0);
    }

    @Test
    void keepsEventsApart() {

        counters.record(Event.SIGN_UP);
        counters.record(Event.SESSION_HELD);

        assertThat(events("session-held")).isEqualTo(1.0);
    }

    /**
     * The tags are the contract a dashboard panel matches on verbatim, so they are pinned here as literals rather than
     * read back off the record, which would only prove the enum equals itself.
     */
    @Test
    void namesEverySeriesTheDashboardsRead() {

        assertThat(Event.open().stream().map(Event::tag)).containsExactly("sign-up", "organization-provisioned",
                "community-created", "session-held", "email-sent");
    }

    /**
     * An event that has never happened must still emit a flat zero. A counter that appeared on first use would leave
     * "nothing has signed up today" and "the sign-up path has been broken since the deploy" looking identical.
     */
    @Test
    void registersEverySeriesBeforeAnythingHasHappened() {

        assertThat(Event.open().stream().map(Event::tag).map(this::events)).containsOnly(0.0)
                .hasSize(Event.open().size());
    }

    /**
     * A call site inside the transaction that makes its event true must not move the counter until that transaction
     * commits. Driven through the real {@code TransactionSynchronizationManager} rather than a stub, because what is
     * being proved is that the increment is registered as a synchronization at all.
     */
    @Test
    void holdsADeferredEventBackUntilTheTransactionCommits() {

        TransactionSynchronizationManager.initSynchronization();
        try {
            counters.recordAfterCommit(Event.COMMUNITY_CREATED);

            assertThat(events("community-created")).isZero();

            TransactionSynchronizationUtils.triggerAfterCommit();

            assertThat(events("community-created")).isEqualTo(1.0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** A transaction that rolls back never triggers the synchronization, so nothing is counted. */
    @Test
    void countsNothingForATransactionThatNeverCommits() {

        TransactionSynchronizationManager.initSynchronization();
        try {
            counters.recordAfterCommit(Event.COMMUNITY_CREATED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(events("community-created")).isZero();
    }

    /**
     * An event nobody contributed fails at the call, inside the caller's transaction, rather than after its write has
     * committed, which is what {@link MeterContribution} promises.
     */
    @Test
    void refusesAnEventNobodyContributedAtTheCallRatherThanAfterTheCommit() {

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> counters.recordAfterCommit(new Event("planted-event")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("No meter is registered for planted-event");

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** With nothing to wait for, the deferred form is the immediate one. */
    @Test
    void countsImmediatelyWhenNoTransactionIsInFlight() {

        counters.recordAfterCommit(Event.COMMUNITY_CREATED);

        assertThat(events("community-created")).isEqualTo(1.0);
    }

    private double events(String event) {

        return registry.get("axonia.usage.events").tag("event", event).counter().count();
    }
}
