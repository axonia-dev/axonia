package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import dev.axonia.events.ActionItemOutcome;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("action_item")
public class ActionItemEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String title;
    private String description;
    private String raisedByUserId;
    private String assigneeUserId;
    private LocalDate dueDate;
    private Long originSessionId;
    private Long checkpointSessionId;
    private ActionItemOutcome outcome;

    @MappedCollection(idColumn = "action_item_id")
    private Set<ActionItemTagRef> tags;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
