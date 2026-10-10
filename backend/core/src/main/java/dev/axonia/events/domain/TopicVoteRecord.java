package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class TopicVoteRecord {

    private Long id;
    private String organizationId;
    private Long topicId;
    private String userId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
