package dev.axonia.privacy.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;

/**
 * The anonymous proof that an erasure ran. {@code erasedAt} is set by the caller from the {@code Clock} that drove the
 * erasure itself, never {@code @CreatedDate}, so the receipt records the same instant the erasure used for its
 * future/past split.
 */
@Getter
@Builder
@Table("erasure_receipt")
class ErasureReceiptEntity {

    @Id
    private Long id;

    private String organizationId;
    private String surrogateId;
    private Instant erasedAt;
    private long deletedRowCount;
    private long clearedRowCount;
    private long anonymisedRowCount;
    private long mediaResidue;
}
