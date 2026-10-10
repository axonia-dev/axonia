package dev.axonia.privacy.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * The anonymous proof that an erasure ran. {@code erasedAt} is the instant the erasure itself used for its future/past
 * split, never a creation timestamp.
 */
@Builder
public record ErasureReceipt(String organizationId, String surrogateId, Instant erasedAt, long deletedRowCount,
        long clearedRowCount, long anonymisedRowCount, long mediaResidue) {
}
