package dev.axonia.onboarding.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One row per subject; {@code subject} is both the primary key and the RLS discriminator for reads and writes alike
 * (see V99). Implements {@link Persistable} because Spring Data JDBC cannot tell an INSERT from an UPDATE on an
 * assigned String {@code @Id} without being told.
 *
 * <p>
 * The steps are an ordered child collection keyed on {@code ordinal}, so the order the planner chose is the order the
 * database keeps rather than something a read has to re-derive.
 */
@Getter
@Builder(toBuilder = true)
@ToString
@Table("onboarding_path")
class OnboardingPathEntity implements Persistable<String> {

    @Id
    private String subject;

    private String variant;

    private String communitySlug;

    private String anchorOrganizationId;

    private Long anchorCommunityId;

    private Instant closedAt;

    @MappedCollection(idColumn = "subject", keyColumn = "ordinal")
    private List<OnboardingStepEntity> steps;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Transient
    private boolean isNew;

    @Override
    public String getId() {
        return subject;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
