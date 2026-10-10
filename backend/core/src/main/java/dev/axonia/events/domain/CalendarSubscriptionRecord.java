package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString(exclude = "tokenHash")
public class CalendarSubscriptionRecord {

    private Long id;
    private String organizationId;
    private Long communityId;
    private String userId;
    private String tokenHash;
    private Instant createdAt;
    private Instant lastFetchedAt;
    private String lastFetchedAgent;
    private Long version;
}
