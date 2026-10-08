package dev.axonia.roundup.internal;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One row per organization that has ever touched the roundup setting; {@code organizationId} is the primary key.
 * Implements {@link Persistable} for the same reason {@code organization.internal.OrganizationEntity} does - the only
 * other assigned-String-key entity here - because Spring Data JDBC cannot tell an INSERT from an UPDATE on a key the
 * caller supplies: the caller sets {@code isNew=true} when creating a fresh row.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Table("roundup_settings")
public class RoundupSettingsEntity implements Persistable<String> {

    @Id
    private String organizationId;

    private boolean enabled;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    /** Transient flag: set to {@code true} on first-time INSERT; {@code false} (default) for updates. */
    @Transient
    private boolean isNew;

    /**
     * {@link Persistable}'s accessor for the key, which here is {@code organizationId}. Annotated {@link Transient}
     * because Spring Data derives a property from any accessor with no matching field, and an {@code id} property would
     * then be mapped to a column {@code roundup_settings} does not have.
     */
    @Override
    @Transient
    public String getId() {
        return organizationId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
