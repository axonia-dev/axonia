package dev.axonia.events.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.mockito.Mockito.verify;

@MockitoSettings
class SessionProducersTest {

    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");

    @Mock
    private UpcomingSessionProducer upcomingSessionProducer;

    @Mock
    private WrapUpProducer wrapUpProducer;

    @Mock
    private AwaitingCloseProducer awaitingCloseProducer;

    @InjectMocks
    private SessionProducers sut;

    @Test
    void produce_runsAllThreeProducers() {

        // when
        sut.produce(NOW);

        // then - order is not part of the contract (see the class javadoc); each is simply called once.
        verify(upcomingSessionProducer).produce(NOW);
        verify(wrapUpProducer).produce(NOW);
        verify(awaitingCloseProducer).produce(NOW);
    }
}
