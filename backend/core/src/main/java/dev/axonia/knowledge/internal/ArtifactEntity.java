package dev.axonia.knowledge.internal;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import dev.axonia.knowledge.ArtifactStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("artifact")
public class ArtifactEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private Long typeId;
    private String title;
    private String description;
    private String url;
    private String contributorUserId;
    private Long provenanceSessionId;
    private Long provenanceSegmentId;
    private String provenanceLabel;
    private String status;
    private String searchDocument;

    // Denormalized appreciation count, owned by the ContributionLoved/Unloved listener.
    // @ReadOnlyProperty: hydrated on read, never written by the artifact write path, so
    // captures and edits can never clobber a concurrent love increment.
    @ReadOnlyProperty
    private Integer loveCount;

    @MappedCollection(idColumn = "artifact_id")
    private Set<ArtifactTagRef> tags;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public boolean isActive() {

        return ArtifactStatus.ACTIVE.name().equals(status);
    }

    public boolean isArchived() {

        return ArtifactStatus.ARCHIVED.name().equals(status);
    }
}
