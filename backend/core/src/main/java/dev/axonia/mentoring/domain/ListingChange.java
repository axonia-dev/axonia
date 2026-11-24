package dev.axonia.mentoring.domain;

import java.time.Instant;

import lombok.Builder;

/** A mentor turning their listing among one community's mentors to a state at a moment. */
@Builder
public record ListingChange(long communityId, String userId, ListingSwitch state, Instant at) {
}
