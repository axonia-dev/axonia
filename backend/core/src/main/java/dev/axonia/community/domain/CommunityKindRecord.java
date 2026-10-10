package dev.axonia.community.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

/** A community kind as it is stored: every column, id and version included, so a write round-trips exactly. */
@Value
@Builder(toBuilder = true)
public class CommunityKindRecord {

    Long id;

    String organizationId;
    String label;

    Instant createdAt;
    Instant updatedAt;
    Long version;
}
