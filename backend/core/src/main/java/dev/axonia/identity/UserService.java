package dev.axonia.identity;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.UsageCounters;
import dev.axonia.identity.internal.AvatarSyncService;
import dev.axonia.identity.internal.UserEntity;
import dev.axonia.identity.internal.UserRepository;
import dev.axonia.identity.internal.UserTimeZoneRow;
import dev.axonia.identity.internal.UserUpsert;
import dev.axonia.media.MediaLinks;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ClaimsReader claimsReader;
    private final AvatarSyncService avatarSyncService;
    private final SignInProviderResolver signInProviderResolver;
    private final UsageCounters usageCounters;
    private final MediaLinks mediaLinks;
    private final Clock clock;

    /**
     * Upserts a profile from token claims - the JIT-provisioning write. {@code @Transactional} here collapses what
     * {@link #upsertProfile} actually does - up to four repository calls: the {@code findByOrganizationIdAndUserId}
     * guard, {@code findActiveSublessByEmail} and {@code attachSub} on the reconciliation arm, or {@code upsert}
     * followed by a read-back on the JIT arm - into one connection, and it closes a real gap the unannotated version
     * had: the blind {@code upsert} and its read-back used to be two separately autocommitted statements, so a crash
     * between them could have left the row written but never observed.
     *
     * <p>
     * {@code @Transactional} here only takes effect for a caller that goes through the Spring proxy, e.g.
     * {@code UserProvisioningFilter}; {@link #me()} reaches this through self-invocation, so its own transaction (or
     * lack of one) governs that path instead - self-invocation never makes the annotation harmful, only inert for that
     * one call site. Also the login-shaped moment the avatar sync hooks into: {@link AvatarSyncService#maybeSync} makes
     * its own cheap DB reads and then hands off to an asynchronous {@code submit(...)} for the Graph call, so running
     * inside this transaction never holds a connection across network I/O the way a synchronous Graph call would.
     */
    @Transactional
    public User provision(IdentityClaims claims) {

        var user = upsertProfile(claims);
        avatarSyncService.maybeSync(claims.organizationId(), claims.userId(), claims.avatarUrl());
        return user;
    }

    /**
     * The claim's own {@code picture} URL never appears in the profile this returns: the avatar address means "the URL
     * we serve". On the reconciliation path the record already in hand carries whatever avatar this member previously
     * had mirrored. On the JIT-upsert path that record isn't in hand - the statement is a blind write - so the row is
     * read back rather than assumed: this is the ON CONFLICT branch on every subsequent sign-in of a non-SCIM member,
     * and a member who already has a mirrored avatar must keep seeing it, not have this call blank it to null.
     * {@code me()}'s org-less branch below carries the same rule: it has no row to read back, so it serves no avatar at
     * all rather than the claim's.
     */
    private User upsertProfile(IdentityClaims claims) {

        var seenAt = clock.instant();
        var email = claims.email();
        // Reconciliation is for a person who has no row under this subject yet. When they already have one, the
        // sub-less row at this address is a SECOND row for that same person - a replacement directory's push under a
        // new address - and attaching the subject to it would move the subject off the row already holding it,
        // straight into uq_identity_user_organization_user. The just-in-time arm below is right in that case: it
        // finds the existing row by subject and refreshes it.
        //
        // The read is here rather than as a NOT EXISTS on attachSub's UPDATE because attachSub returns void: a
        // statement that matched no row would leave this method returning a User built from a reconciliation that
        // never happened, with no caller able to tell. Making that detectable means returning a count and branching
        // on it here, which is this condition with extra steps.
        //
        // Known and accepted consequence: this stops the 500, it does not merge the two rows. The pushed row stays
        // active and sub-less for good, so the Organization counts the person twice (countPresentByOrganizationId and
        // UserSearchRepositoryImpl both see it), the profile served keeps the OLD row's scim_id, department,
        // businessUnit and team, and whatever the directory pushed at the new address never reaches them. Nothing
        // logs or counts that a reconciliation was suppressed. Merging the two is a separate piece of work, not this
        // guard's; SCIM adoption removes the way in for customers who have it, and for nobody else.
        if (email != null && !email.isBlank()
                && userRepository.findByOrganizationIdAndUserId(claims.organizationId(), claims.userId()).isEmpty()) {
            var reconcilable = userRepository.findActiveSublessByEmail(claims.organizationId(), email);
            if (reconcilable.isPresent()) {
                var record = reconcilable.get();
                userRepository.attachSub(record.getId(), claims.userId(), seenAt);
                // memberSince and avatarIsOwn come straight off the reconciled row; lastActive is seenAt itself -
                // the instant attachSub just wrote - not record.getLastSeenAt(), which this call has made stale.
                return User.builder().id(claims.userId()).displayName(record.getDisplayName()).email(record.getEmail())
                        .avatarUrl(resolveAvatarUrl(record)).memberStatus(UserDirectory.MemberStatus.PRESENT)
                        .memberSince(record.getCreatedAt()).lastActive(seenAt)
                        .avatarIsOwn(record.getAvatarOverrideKey() != null).timeZone(resolveTimeZone(record))
                        .observedTimeZone(record.getObservedTimeZone())
                        .timeZoneIsOwn(record.getDeclaredTimeZone() != null).build();
            }
        }
        userRepository.upsert(UserUpsert.builder().organizationId(claims.organizationId()).userId(claims.userId())
                .displayName(claims.displayName()).email(claims.email()).scimId(UUID.randomUUID().toString()).build(),
                seenAt);
        var row = userRepository.findByOrganizationIdAndUserId(claims.organizationId(), claims.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioned row " + claims.organizationId() + "|" + claims.userId() + " not readable back"));
        if (bornOnThisSignIn(row)) {
            usageCounters.record(UsageCounters.Event.SIGN_UP);
        }
        return toUser(row);
    }

    /**
     * Whether the upsert above inserted this row rather than refreshing one that was already there - the only thing on
     * any of these paths that is a sign-up. The statement writes {@code version = 0} on insert and
     * {@code version = identity_user.version + 1} on conflict, so a row still at its first version is one no statement
     * has ever updated, and this one always updates a row it found. That is a fact about columns this application
     * writes itself, not about the database's opinion of what it just did.
     *
     * <p>
     * Null-guarded rather than unboxed because the column's NOT NULL only holds for a row that came from the database;
     * an entity assembled in a test has whatever version it was given.
     *
     * <p>
     * Two genuinely concurrent first requests for the same person can both read back the losing statement's version and
     * count nothing at all. That is a deliberate under-count of at most one, on a metric read as a daily trend, and it
     * is the safe direction: the alternative races the other way and reports two sign-ups for one person.
     */
    private static boolean bornOnThisSignIn(UserEntity row) {

        return Long.valueOf(0L).equals(row.getVersion());
    }

    /** The caller's own profile; provisions on the spot if absent. Org-less callers get a claims-only view. */
    @Transactional
    public User me() {

        var claims = claimsReader.read();
        if (claims.organizationId() == null) {
            // A social sign-in with no org claim: the person exists, their per-org profile doesn't yet, and there is
            // no row to mirror an avatar into or read one back from. No AvatarSyncService run can reach this caller
            // either (maybeSync looks the row up by organization and finds none), so the claim's picture URL - a
            // third-party host - must not be served: it would be the one avatar address the img-src policy can't cover.
            // The UI falls back to initials, the same as any member whose mirror hasn't run yet.
            return User.builder().id(claims.userId()).displayName(claims.displayName()).email(claims.email())
                    .signInProvider(signInProviderResolver.resolve(null))
                    .memberStatus(UserDirectory.MemberStatus.PRESENT).build();
        }
        var provider = signInProviderResolver.resolve(claims.organizationId());
        return userRepository.findByOrganizationIdAndUserId(claims.organizationId(), claims.userId()).map(this::toUser)
                .orElseGet(() -> provision(claims)).toBuilder().signInProvider(provider).build();
    }

    /**
     * The member's own answer about where they sit in time, which wins over the observed reading without stopping it.
     * Returns the refreshed profile so the caller re-renders from one response, the same shape the avatar routes have.
     */
    @Transactional
    public User declareTimeZone(String zone) {

        var claims = claimsReader.read();
        userRepository.updateDeclaredTimeZone(claims.organizationId(), claims.userId(), zone, clock.instant());
        return me();
    }

    /** Drops the declaration; the observation that kept refreshing underneath becomes the effective clock again. */
    @Transactional
    public User clearDeclaredTimeZone() {

        var claims = claimsReader.read();
        userRepository.clearDeclaredTimeZone(claims.organizationId(), claims.userId(), clock.instant());
        return me();
    }

    /**
     * What the browser reported. Never touches a declaration: the mirror keeps refreshing underneath one, so a member
     * who clears theirs lands on where they are now rather than where they were when they declared.
     */
    @Transactional
    public User observeTimeZone(String zone) {

        var claims = claimsReader.read();
        userRepository.updateObservedTimeZone(claims.organizationId(), claims.userId(), zone, clock.instant());
        return me();
    }

    /**
     * The effective clock of each named member, omitting anyone who has none.
     *
     * <p>
     * Partial where {@link #displayNames} is total, and deliberately: a name has a neutral stand-in, a clock does not.
     * The empty guard is load-bearing rather than an optimisation - {@code IN ()} is not valid SQL.
     */
    @Transactional(readOnly = true)
    public Map<String, String> timeZones(String organizationId, Collection<String> userIds) {

        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findTimeZones(organizationId, userIds).stream()
                .collect(Collectors.toMap(UserTimeZoneRow::userId, UserTimeZoneRow::timeZone));
    }

    /**
     * Batch resolve for display, scoped to the caller's organization. Total, not partial: an id that resolves to no row
     * comes back as a {@link UserDirectory.MemberStatus#ERASED} entry under the port's neutral label, the same
     * substitution {@link dev.axonia.identity.internal.UserDirectoryAdapter#byIds} makes, rather than being silently
     * omitted from the batch. Returns the minimized {@link DirectoryUser} projection, never the caller's email.
     */
    @Transactional(readOnly = true)
    public List<DirectoryUser> resolve(Collection<String> ids) {

        var organizationId = claimsReader.read().organizationId();
        if (organizationId == null) {
            // An org-less (fresh social) token has no per-org profiles to resolve - explicit
            // empty result rather than relying on organization_id's NOT NULL to blank the query.
            return List.of();
        }
        // distinct(): a duplicate input id must come back as one output entry, not two.
        var uniqueIds = ids.stream().distinct().toList();
        // toMap never sees a duplicate key: (organization_id, user_id) is unique, so each of these
        // already-distinct ids matches at most one row.
        var found = userRepository.findByOrganizationIdAndUserIdIn(organizationId, uniqueIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, this::toDirectoryUser));
        return uniqueIds.stream().map(id -> found.getOrDefault(id, erasedDirectoryUser(id))).toList();
    }

    private static DirectoryUser erasedDirectoryUser(String id) {

        return DirectoryUser.builder().id(id).displayName(UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL)
                .memberStatus(UserDirectory.MemberStatus.ERASED).build();
    }

    /** Display name for a specific user in a given organization; returns empty if not found. */
    @Transactional(readOnly = true)
    public Optional<String> displayName(String organizationId, String userId) {

        return userRepository.findByOrganizationIdAndUserId(organizationId, userId).map(UserEntity::getDisplayName);
    }

    /** Batch display-name lookup for users in a given organization; unknown ids are omitted from the result. */
    @Transactional(readOnly = true)
    public Map<String, String> displayNames(String organizationId, Collection<String> userIds) {

        return userRepository.findByOrganizationIdAndUserIdIn(organizationId, userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getDisplayName));
    }

    /**
     * Override, then mirror, then nothing. One order, applied where the profile is read rather than at each call site,
     * so every surface that renders a person agrees.
     */
    private String resolveAvatarUrl(UserEntity entity) {

        var key = entity.getAvatarOverrideKey() != null ? entity.getAvatarOverrideKey() : entity.getAvatarMediaKey();
        return key == null ? null : mediaLinks.urlFor(key);
    }

    /**
     * Declared over observed, and null when neither is set. One order, applied where the profile is read rather than at
     * each call site, exactly as {@link #resolveAvatarUrl} is - and the null is the point: no reader may mistake an
     * unmeasured member for a measured one.
     */
    private static String resolveTimeZone(UserEntity entity) {

        return entity.getDeclaredTimeZone() != null ? entity.getDeclaredTimeZone() : entity.getObservedTimeZone();
    }

    User toUser(UserEntity entity) {

        return User.builder().id(entity.getUserId()).displayName(entity.getDisplayName()).email(entity.getEmail())
                .avatarUrl(resolveAvatarUrl(entity)).department(entity.getDepartment())
                .businessUnit(entity.getBusinessUnit()).team(entity.getTeam())
                .memberStatus(UserDirectory.statusOf(entity.getActive())).memberSince(entity.getCreatedAt())
                .lastActive(entity.getLastSeenAt()).avatarIsOwn(entity.getAvatarOverrideKey() != null)
                .timeZone(resolveTimeZone(entity)).observedTimeZone(entity.getObservedTimeZone())
                .timeZoneIsOwn(entity.getDeclaredTimeZone() != null).build();
    }

    DirectoryUser toDirectoryUser(UserEntity entity) {

        return DirectoryUser.builder().id(entity.getUserId()).displayName(entity.getDisplayName())
                .avatarUrl(resolveAvatarUrl(entity)).department(entity.getDepartment())
                .businessUnit(entity.getBusinessUnit()).team(entity.getTeam())
                .memberStatus(UserDirectory.statusOf(entity.getActive())).build();
    }
}
