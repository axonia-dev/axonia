package dev.axonia.knowledge.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("artifact_attachment")
public class AttachmentEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long artifactId;
    private Long sessionId;
    private Long segmentId;
    private String attachedByUserId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
