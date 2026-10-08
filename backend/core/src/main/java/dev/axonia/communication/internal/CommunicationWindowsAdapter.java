package dev.axonia.communication.internal;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.communication.CommunicationWindows;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CommunicationWindowsAdapter implements CommunicationWindows {

    private final CommunicationProperties properties;

    @Override
    public Duration wrapUpMaxAge() {

        return properties.wrapUpMaxAge();
    }

    @Override
    public Duration awaitingCloseMaxAge() {

        return properties.awaitingCloseMaxAge();
    }
}
