package dev.axonia.recognition.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import dev.axonia.recognition.ContributionType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("contribution_reaction")
public class ReactionEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private ContributionType contributionType;
    private Long contributionId;
    private String reactorUserId;
    private String emoji;
    private Long definitionId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
