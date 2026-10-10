package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.api.spi.MessageProducer;
import dev.axonia.communication.application.port.out.OutboxRetention;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One organization's work for one tick of any of the three schedules: {@link SweepUnit#produce} enqueues whatever is
 * due, {@link SweepUnit#drain} sends whatever is now claimable, {@link SweepUnit#retain} expires whatever has aged out.
 * Produce and drain are independently callable methods (they no longer share one tick), so this class's job is proving
 * each calls only its own share of work - never one of the others as a side effect - rather than proving an ordering
 * between calls that no longer happen together.
 */
@ExtendWith(MockitoExtension.class)
class SweepUnitTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Mock
    private MessageProducer sessions;
    @Mock
    private MessageProducer roundup;
    @Mock
    private OutboxDrain drain;
    @Mock
    private OutboxRetention retention;
    @Mock
    private CommunicationSettings properties;

    @Test
    void produceOnlyEnqueuesAndNeverDrains() {

        var unit = sweepUnit();

        unit.produce("org-1");

        verify(sessions).produce(NOW);
        verify(roundup).produce(NOW);
        verifyNoInteractions(drain);
    }

    /**
     * The isolation the class's own javadoc promises: one producer throwing must not cost every producer after it its
     * tick. Both are handed the SAME instant even so - a tick is one moment, and a producer that computed its own would
     * put two messages from one tick in different periods.
     */
    @Test
    void oneProducerFailingStillLeavesTheNextOneItsTick() {

        doThrow(new IllegalStateException("boom")).when(sessions).produce(NOW);
        var unit = sweepUnit();

        unit.produce("org-1");

        verify(roundup).produce(NOW);
    }

    @Test
    void drainOnlyDrainsAndNeverProduces() {

        var unit = sweepUnit();
        var deadline = NOW.plus(Duration.ofSeconds(45));

        unit.drain("org-1", deadline);

        verify(drain).drain(deadline);
        verifyNoInteractions(sessions, roundup, retention);
    }

    /**
     * Correction 1: {@code now} and {@code cutoff} reach {@link OutboxRetention#expire} as two distinct instants, both
     * derived from the SAME {@code Clock} reading - {@code cutoff} is {@code now} minus
     * {@link CommunicationSettings#outboxRetention}, never a second, later reading of the clock.
     */
    @Test
    void retainOnlyExpiresAndNeverProducesOrDrains() {

        when(properties.outboxRetention()).thenReturn(Duration.ofDays(90));
        when(properties.retentionBatchSize()).thenReturn(200);
        var unit = sweepUnit();

        unit.retain("org-1");

        verify(retention).expire(NOW, NOW.minus(Duration.ofDays(90)), 200);
        verifyNoInteractions(sessions, roundup, drain);
    }

    private SweepUnit sweepUnit() {

        return new SweepUnit(List.of(sessions, roundup), drain, Clock.fixed(NOW, ZoneOffset.UTC), retention,
                properties);
    }
}
