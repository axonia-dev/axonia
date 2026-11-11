package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class FeedbackRecord {

    private Long id;
    private String organizationId;
    private Long sessionId;
    private String userId;
    private int enjoyed;
    private int learned;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
