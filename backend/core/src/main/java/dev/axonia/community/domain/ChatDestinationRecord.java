package dev.axonia.community.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

/**
 * A chat destination as it is stored: every column, id and version included, so a write round-trips exactly. The
 * webhook URL is a bearer token for a room, so {@code toString} never prints it.
 */
@Value
@Builder(toBuilder = true)
@ToString(exclude = "webhookUrl")
public class ChatDestinationRecord {

    Long id;

    String organizationId;
    Long communityId;
    String name;
    String webhookUrl;
    boolean broken;
    int consecutiveFailures;
    Instant lastPostedAt;
    Instant lastFailureAt;
    String lastError;

    Instant createdAt;
    Instant updatedAt;
    Long version;
}
