package dev.axonia.events.domain;

import java.time.Instant;
import java.util.Set;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class TopicRecord {

    private Long id;
    private String organizationId;
    private Long communityId;
    private TopicKind kind;
    private String title;
    private String description;
    private String proposerUserId;
    private TopicStatus status;
    private Set<TopicTag> tags;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
