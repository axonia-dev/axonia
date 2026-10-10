package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.application.port.out.MailBatch;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt;
import dev.axonia.shared.api.ratelimit.RateLimitStore;
import dev.axonia.shared.api.ratelimit.RateLimiters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a message costs before it reaches the transport: one send slot, and - only for the first message that gets one -
 * one connection. Both used to be paid a row earlier, where a row the gate suppressed spent a slot on nothing and a
 * slice that sent nothing still paid a full connect, STARTTLS and AUTH.
 */
class PacedMailBatchTest {

    private static final OutgoingMail MAIL = new OutgoingMail("member@example.com", "Subject", "<p>html</p>", "text",
            null);
    private static final Runnable NOBODY = () -> {
    };

    /** Counts what the provider actually sees: connections opened and closed, and the messages each carried. */
    private static final class CountingConnections {

        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger sent = new AtomicInteger();

        MailBatch open() {

            opened.incrementAndGet();
            return new MailBatch() {

                @Override
                public void send(OutgoingMail mail) {

                    sent.incrementAndGet();
                }

                @Override
                public void close() {

                    closed.incrementAndGet();
                }
            };
        }
    }

    /**
     * A row the delivery gate suppresses returns without ever touching the transport. Nothing about it should reach the
     * provider, and - the bug this pins - nothing about it should spend a slot of the shared send budget either.
     */
    @Test
    void aBatchNobodySendsThroughOpensNothingAndSpendsNothing() {

        var connections = new CountingConnections();
        var slots = new AtomicInteger();

        try (var batch = new PacedMailBatch(connections::open, () -> slots.incrementAndGet() > 0, NOBODY)) {
            assertThat(batch).isNotNull();
        }

        assertThat(connections.opened).hasValue(0);
        assertThat(connections.closed).hasValue(0);
        assertThat(slots).hasValue(0);
    }

    /** One connection for the whole slice, one slot for each message on it. */
    @Test
    void buysOneSlotPerMessageAndOpensOneConnectionForAllOfThem() {

        var connections = new CountingConnections();
        var slots = new AtomicInteger();

        try (var batch = new PacedMailBatch(connections::open, () -> slots.incrementAndGet() > 0, NOBODY)) {
            batch.send(MAIL);
            batch.send(MAIL);
            batch.send(MAIL);
        }

        assertThat(slots).hasValue(3);
        assertThat(connections.opened).hasValue(1);
        assertThat(connections.sent).hasValue(3);
        assertThat(connections.closed).hasValue(1);
    }

    /**
     * The slot is bought before the connection, not after: a tick whose window has already closed must not pay a
     * connect to discover it has nothing to send. {@code drainTick} walks organizations one at a time against one
     * global budget, so paying it anyway would cost four connections per remaining tenant, every tick.
     */
    @Test
    void aClosedSendWindowSendsNothingAndOpensNothing() {

        var connections = new CountingConnections();

        try (var batch = new PacedMailBatch(connections::open, () -> false, NOBODY)) {
            assertThatThrownBy(() -> batch.send(MAIL)).isInstanceOf(SendWindowClosedException.class);
        }

        assertThat(connections.opened).hasValue(0);
        assertThat(connections.closed).hasValue(0);
    }

    /**
     * Whatever goes wrong opening the connection, nothing was sent and every row behind this one would fail
     * identically, so it is raised as the one exception the drain answers by releasing rows unmarked. A plain
     * {@code RuntimeException} would be recorded as this row's own failure and burn an attempt against it.
     */
    @Test
    void aConnectionThatCannotBeOpenedIsAnAccountRefusalWhateverWentWrong() {

        try (var batch = new PacedMailBatch(() -> {
            throw new IllegalStateException("the transport factory is misconfigured");
        }, () -> true, NOBODY)) {
            assertThatThrownBy(() -> batch.send(MAIL)).isInstanceOf(MailAccountRefusedException.class);
        }
    }

    /**
     * Every message the connection took is reported as accepted, and only those: a refused open and a refused message
     * report nothing, because whether the drain holds its mail turns on whether the server took any of it.
     */
    @Test
    void reportsEveryMessageTheConnectionTookAndNoOtherAsAccepted() {

        var connections = new CountingConnections();
        var accepted = new AtomicInteger();

        try (var batch = new PacedMailBatch(connections::open, () -> true, accepted::incrementAndGet)) {
            batch.send(MAIL);
            batch.send(MAIL);
        }
        try (var batch = new PacedMailBatch(() -> {
            throw new MailAccountRefusedException("the mail transport refused the connection", null);
        }, () -> true, accepted::incrementAndGet)) {
            assertThatThrownBy(() -> batch.send(MAIL)).isInstanceOf(MailAccountRefusedException.class);
        }
        try (var batch = new PacedMailBatch(() -> new MailBatch() {

            @Override
            public void send(OutgoingMail mail) {

                throw new MailAccountRefusedException("daily quota exceeded", null);
            }

            @Override
            public void close() {

            }
        }, () -> true, accepted::incrementAndGet)) {
            assertThatThrownBy(() -> batch.send(MAIL)).isInstanceOf(MailAccountRefusedException.class);
        }

        assertThat(accepted).hasValue(2);
    }

    /**
     * The fix, end to end at the level a message actually experiences it: the pace refuses twice, the batch waits both
     * times, and the message still goes out on this tick rather than on one a minute later.
     */
    @Test
    void aPacedRefusalCostsAWaitRatherThanTheMessage() {

        var connections = new CountingConnections();
        var waits = new AtomicInteger();
        var pacing = new SendPacing(pacedLimiter(), Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC),
                wait -> waits.incrementAndGet() > 0);
        var deadline = Instant.parse("2026-08-01T12:00:45Z");

        try (var batch = new PacedMailBatch(connections::open, () -> pacing.awaitSendSlot(deadline, () -> false),
                NOBODY)) {
            batch.send(MAIL);
        }

        assertThat(waits).hasValue(2);
        assertThat(connections.sent).hasValue(1);
    }

    private static dev.axonia.shared.api.ratelimit.RateLimiter pacedLimiter() {

        var queued = new ArrayDeque<>(List.of(RateLimitAttempt.refused(Duration.ofMillis(100)),
                RateLimitAttempt.refused(Duration.ofMillis(100)), RateLimitAttempt.allowed()));
        RateLimitStore store = (key, capacity, window) -> queued.size() > 1 ? queued.poll() : queued.peek();
        return new RateLimiters(store, new SimpleMeterRegistry()).create("outbox-send", 10, Duration.ofSeconds(1));
    }
}
