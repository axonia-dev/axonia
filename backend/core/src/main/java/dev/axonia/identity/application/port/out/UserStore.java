package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.AvatarSyncUpdate;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserSearch;
import dev.axonia.identity.domain.UserTimeZoneRow;
import dev.axonia.identity.domain.UserUpsert;

/** The directory of people, one row per (organization, subject). Every write is keyed as its read is. */
public interface UserStore {

    Optional<UserAccount> findByOrganizationIdAndUserId(String organizationId, String userId);

    List<UserAccount> findByOrganizationIdAndUserIdIn(String organizationId, Collection<String> userIds);

    List<UserAccount> findByOrganizationId(String organizationId, int limit);

    Optional<UserAccount> findByOrganizationIdAndScimId(String organizationId, String scimId);

    Optional<UserAccount> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    /** Matches the address case-insensitively. */
    Optional<UserAccount> findByOrganizationIdAndEmail(String organizationId, String email);

    /** The subjects of everybody present in one organization, ordered; a never-signed-in member has none. */
    List<String> findPresentUserIds(String organizationId);

    /** An active row at this address that nobody has signed in as yet, the one a sign-in may reconcile onto. */
    Optional<UserAccount> findActiveSublessByEmail(String organizationId, String email);

    /** Whoever is present at one address, preferring the row somebody signed in as, then the older one. */
    Optional<UserAccount> findActiveByEmail(String organizationId, String email);

    List<UserAccount> search(UserSearch search);

    /** The effective clock of each named member, omitting anyone who has none. Never called with no ids. */
    List<UserTimeZoneRow> findTimeZones(String organizationId, Collection<String> userIds);

    UserAccount save(UserAccount user);

    void attachSub(Long id, String userId, Instant seenAt);

    /** Race-safe just-in-time insert or refresh of one sign-in's profile. */
    void upsert(UserUpsert user, Instant seenAt);

    void updateSyncedAvatar(AvatarSyncUpdate update);

    void touchAvatarSyncedAt(String organizationId, String userId, Instant syncedAt);

    void clearSyncedAvatar(String organizationId, String userId, Instant syncedAt);

    void updateAvatarOverride(AvatarOverrideUpdate update, Instant updatedAt);

    void clearAvatarOverride(String organizationId, String userId, Instant updatedAt);

    /** A repeat of the same reading is a no-op. */
    void updateObservedTimeZone(String organizationId, String userId, String zone, Instant updatedAt);

    void updateDeclaredTimeZone(String organizationId, String userId, String zone, Instant updatedAt);

    void clearDeclaredTimeZone(String organizationId, String userId, Instant updatedAt);
}
