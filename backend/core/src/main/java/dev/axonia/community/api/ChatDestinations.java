package dev.axonia.community.api;

import java.time.Instant;
import java.util.List;

import dev.axonia.communication.api.ChatChannelStatus;
import dev.axonia.communication.api.spi.ChatDestinationsPort.ChatFailure;

/** A community's chat destinations as the drain sees them; every method is org-explicit. */
public interface ChatDestinations {

    /** A destination removed, or whose stored URL no longer parses, reads as {@code NotConfigured}. */
    ChatChannelStatus statusFor(String organizationId, long destinationId);

    List<Long> destinationIdsFor(String organizationId, long communityId);

    void recordChatDelivered(String organizationId, long destinationId, Instant at);

    void recordChatFailure(String organizationId, long destinationId, ChatFailure failure, int brokenAfter);
}
