package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.identity.domain.DirectoryGroup;

/** An organization's synced directory groups and their reconciliation keys. */
public interface DirectoryGroupStore {

    Optional<DirectoryGroup> findByOrganizationIdAndScimId(String organizationId, String scimId);

    Optional<DirectoryGroup> findByOrganizationIdAndReconciliationKey(String organizationId, String reconciliationKey);

    List<DirectoryGroup> findByOrganizationIdAndDisplayName(String organizationId, String displayName);

    List<DirectoryGroup> findByOrganizationIdAndReconciliationKeyIn(String organizationId,
            Collection<String> reconciliationKeys);

    List<DirectoryGroup> findByOrganizationId(String organizationId, int limit);

    List<DirectoryGroup> findByOrganizationIdAndConfirmedAtIsNull(String organizationId);

    /** Every synced group a user belongs to, by the user's SCIM id. */
    List<DirectoryGroup> findByOrganizationIdAndMemberScimId(String organizationId, String userScimId);

    /**
     * Saves a group. A second group under one {@code (organization, reconciliation key)} fails with
     * {@link org.springframework.dao.DuplicateKeyException}.
     */
    DirectoryGroup save(DirectoryGroup group);

    void delete(DirectoryGroup group);

    /** Rewrites every group's key to its external id, leaving a group without one untouched. Answers how many moved. */
    int rekeyToExternalId(String organizationId);

    /** The confirmed groups {@link #rekeyToExternalId} is about to move. */
    List<DirectoryGroup> findConfirmedGroupsRekeyingToExternalId(String organizationId);

    /** Rewrites every group's key to its display name. Answers how many moved. */
    int rekeyToDisplayName(String organizationId);

    /** The confirmed groups {@link #rekeyToDisplayName} is about to move. */
    List<DirectoryGroup> findConfirmedGroupsRekeyingToDisplayName(String organizationId);

    /** Takes the confirmation back off the named groups. Answers how many were held. */
    int holdByIds(String organizationId, Collection<Long> ids, Instant updatedAt);

    int countGroupsWithoutExternalId(String organizationId);
}
