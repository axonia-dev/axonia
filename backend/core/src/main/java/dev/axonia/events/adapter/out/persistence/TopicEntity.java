package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("topic")
public class TopicEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private TopicKind kind;
    private String title;
    private String description;
    private String proposerUserId;
    private TopicStatus status;

    @MappedCollection(idColumn = "topic_id")
    private Set<TopicTagRef> tags;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
