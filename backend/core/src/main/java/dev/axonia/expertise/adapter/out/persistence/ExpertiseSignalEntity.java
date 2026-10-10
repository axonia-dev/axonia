package dev.axonia.expertise.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Persistence entity for the {@code expertise_signal} table.
 *
 * <p>
 * No {@code @Version} column exists in the schema — expertise_signal rows are immutable once written
 * (delete-then-reinsert via {@link SignalWriter}), so optimistic locking is unnecessary.
 */
@Getter
@Builder
@ToString
@Table("expertise_signal")
public class ExpertiseSignalEntity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String userId;
    private String tag;
    private String signalType;
    private String sourceType;
    private Long sourceId;
    private Instant occurredAt;
}
