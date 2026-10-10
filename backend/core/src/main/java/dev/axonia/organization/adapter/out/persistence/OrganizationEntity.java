package dev.axonia.organization.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Registry row: one per organization; {@code id} equals the organization identifier string. Implements
 * {@link Persistable} so Spring Data JDBC can distinguish INSERT (new row) from UPDATE on an assigned String
 * {@code @Id} - the caller sets {@code isNew=true} when creating a fresh record.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Table("organization")
public class OrganizationEntity implements Persistable<String> {

    @Id
    private String id;

    private String name;

    private OrganizationKind kind;

    @Builder.Default
    private OrganizationLifecycleState lifecycleState = OrganizationLifecycleState.ACTIVE;

    private Instant closedAt;

    private String closedBy;

    /** Organization-wide kill switch for outbound member messaging. On by default. */
    @Builder.Default
    private boolean communicationsEnabled = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Transient flag: set to {@code true} on first-time INSERT; {@code false} (default) for updates. */
    @Transient
    private boolean isNew;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
