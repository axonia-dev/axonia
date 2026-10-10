package dev.axonia.community.domain;

import java.time.Instant;

import dev.axonia.community.api.CommunityRole;
import lombok.Builder;
import lombok.Value;

/** A membership as it is stored: every column, id and version included, so a write round-trips exactly. */
@Value
@Builder(toBuilder = true)
public class MembershipRecord {

    Long id;

    String organizationId;
    Long communityId;
    String userId;
    CommunityRole role;

    Instant createdAt;
    Instant updatedAt;
    Long version;
}
