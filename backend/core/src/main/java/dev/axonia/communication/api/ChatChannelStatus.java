package dev.axonia.communication.api;

import java.net.URI;

/** Where a subject's post goes right now, or why it does not go anywhere. */
public sealed interface ChatChannelStatus {

    record NotConfigured() implements ChatChannelStatus {
    }

    record Broken() implements ChatChannelStatus {
    }

    record Ready(URI url) implements ChatChannelStatus {
    }
}
