package dev.axonia.learning;

import java.time.Instant;

import dev.axonia.MemberTag;
import lombok.Builder;

/**
 * A member's own statement that they want to learn a tag, in one community, stored as typed. Never evidence, never a
 * request for a session.
 */
@Builder
public record LearningIntent(long communityId, String userId, String tag, Instant declaredAt) implements MemberTag {
}
