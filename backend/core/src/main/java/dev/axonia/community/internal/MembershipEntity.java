package dev.axonia.community.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import dev.axonia.community.CommunityRole;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("memberships")
public class MembershipEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String userId;
    private CommunityRole role;

    @Column("joined_at")
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
