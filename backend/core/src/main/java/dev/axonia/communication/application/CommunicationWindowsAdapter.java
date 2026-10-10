package dev.axonia.communication.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.CommunicationWindows;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommunicationWindowsAdapter implements CommunicationWindows {

    private final CommunicationSettings properties;

    @Override
    public Duration wrapUpMaxAge() {

        return properties.wrapUpMaxAge();
    }

    @Override
    public Duration awaitingCloseMaxAge() {

        return properties.awaitingCloseMaxAge();
    }
}
