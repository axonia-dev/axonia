package dev.axonia.communication.internal;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationWindowsAdapterTest {

    private static final CommunicationProperties PROPERTIES = CommunicationPropertiesMother
            .withWindows(Duration.ofDays(45), Duration.ofDays(30));

    private final CommunicationWindowsAdapter sut = new CommunicationWindowsAdapter(PROPERTIES);

    @Test
    void wrapUpMaxAge_readsFromCommunicationProperties() {

        assertThat(sut.wrapUpMaxAge()).isEqualTo(Duration.ofDays(45));
    }

    @Test
    void awaitingCloseMaxAge_readsFromCommunicationProperties() {

        assertThat(sut.awaitingCloseMaxAge()).isEqualTo(Duration.ofDays(30));
    }
}
