package dev.axonia.expertise.domain;

import java.time.Instant;

import dev.axonia.expertise.api.MemberTag;
import lombok.Builder;

/**
 * A member's own statement that they are open to questions on a tag, in one community. Not evidence: it never becomes a
 * signal, and nothing that reads the signals can see it.
 */
@Builder
public record Declaration(long communityId, String userId, String tag, Instant declaredAt) implements MemberTag {
}
