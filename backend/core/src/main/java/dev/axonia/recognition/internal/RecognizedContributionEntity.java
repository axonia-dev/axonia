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
@Table("recognized_contribution")
public class RecognizedContributionEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private ContributionType contributionType;
    private Long contributionId;
    private Long communityId;
    private String title;
    private Instant occurredAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
