package dev.axonia.events.adapter.out.communication;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.CommunicationWindows;
import dev.axonia.events.application.port.out.WrapUpWindow;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CommunicationWrapUpWindow implements WrapUpWindow {

    private final CommunicationWindows communicationWindows;

    @Override
    public Duration maxAge() {

        return communicationWindows.wrapUpMaxAge();
    }
}
