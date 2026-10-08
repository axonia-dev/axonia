package dev.axonia;

import java.time.Instant;

import lombok.Builder;

/**
 * One person's erasure on the person axis. The sibling of {@link MemberErasure}, and deliberately narrower: it names no
 * Organization, because the rows it governs belong to none.
 */
@Builder
public record PersonErasure(String subject, String surrogateId, Instant erasedAt) {
}
