package dev.axonia.recognition.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("reaction_definition")
public class ReactionDefinitionEntity implements Entity {

    /** The two states a definition can be in. Declined is a delete, not a state. */
    public static final String PENDING = "PENDING";

    public static final String APPROVED = "APPROVED";

    @Id
    private Long id;

    private String organizationId;
    private String name;
    private String imageKey;
    private String state;
    private String proposedByUserId;
    private String approvedByUserId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
