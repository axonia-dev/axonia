package dev.axonia.events.adapter.out.communication;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.CommunicationWindows;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationWrapUpWindowTest {

    @Test
    void maxAge_isTheWrapUpProducersOwnFloor() {

        var windows = new CommunicationWindows() {

            @Override
            public Duration wrapUpMaxAge() {

                return Duration.ofDays(30);
            }

            @Override
            public Duration awaitingCloseMaxAge() {

                return Duration.ofDays(7);
            }
        };

        assertThat(new CommunicationWrapUpWindow(windows).maxAge()).isEqualTo(Duration.ofDays(30));
    }
}
