package dev.axonia.privacy.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.OrganizationOwners;
import dev.axonia.community.api.Communities;
import dev.axonia.identity.api.spi.IdentityAccounts;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.identity.api.spi.OrganizationMembershipSource.OrganizationMembership;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;
import dev.axonia.privacy.application.ErasureBlocker.BlockerKind;
import dev.axonia.privacy.domain.SurrogateIds;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.personaldata.PersonErasure;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import dev.axonia.shared.api.tenancy.SubjectScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A self-serve member's erasure of their own account.
 *
 * <p>
 * <strong>What is atomic, and what is not.</strong> Each Organization's erasure is one transaction, in
 * {@link MemberPurge}; the person-rooted step is one transaction too, in {@link PersonPurge}; the identity-provider
 * deletion is deliberately last and outside both. {@code erase} itself is not transactional, and cannot be made so: it
 * loops every hub the subject belongs to through its own {@code OrganizationScope.runAs(hubId, ...)}, and a Postgres
 * connection chooses its RLS scope ({@code app.current_org}) once, when a transaction acquires it - so one transaction
 * cannot span two different hubs. A subject who belongs to several hubs therefore has no whole-account atomicity across
 * them, and none is achievable without an outbox or a saga, which this erasure does not implement. What atomicity does
 * hold: a failure partway through the Organizations leaves the earlier ones genuinely erased and the later ones
 * genuinely untouched, never a hub half-erased, and re-running resumes cleanly, because an already-erased Organization
 * has no membership left to find.
 *
 * <p>
 * {@link OrganizationMembershipSource#membershipsFor} is an identity provider read and crosses tenants freely, but
 * every local registry/role/community read this class does for those Organizations is RLS-scoped and private by
 * default. Reading hub A's data while ambiently scoped to hub B (or to nothing) would simply see zero rows, not a
 * permission error, so every per-Organization read here - the guard checks and the erasure itself - runs inside its own
 * {@code OrganizationScope.runAs(organizationId, ...)}, the same "one org id, one scope" shape {@code MemberPurge}
 * uses, one Organization at a time rather than one scope for the whole subject.
 *
 * <p>
 * Person-rooted rows are the exception to all of that, and get their own step: they carry no Organization, are scoped
 * on {@code app.current_subject} instead, and are erased exactly once, as one transaction across every registered
 * eraser - see {@link #erasePersonRootedData}.
 *
 * <p>
 * An identity provider deletion failure is caught and rethrown as {@link AccountErasureIncompleteException}, which
 * names both halves plainly - the data is erased, only the identity-provider sign-out remains - because a bare
 * {@code RestClientException} propagating up would read as "erasure failed, try again," pointing the person in exactly
 * the wrong direction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountErasure {

    private static final String BLOCKED_CODE = "ACCOUNT_ERASURE_BLOCKED";

    private final OrganizationMembershipSource memberships;
    private final OrganizationRegistry organizations;
    private final MemberDataRights memberDataRights;
    private final OrganizationOwners roleBindings;
    private final Communities communities;
    private final MemberPurge purge;
    private final IdentityAccounts identityAccounts;
    private final PersonPurge personPurge;
    private final Clock clock;

    /**
     * Every Organization the subject belongs to that exercises its members' data rights itself (G1: enterprise
     * membership, see {@link MemberDataRightsPolicy}) and every hub they solely own (G2), as data rather than an
     * exception, so a caller such as the preflight endpoint can render every reason at once.
     */
    public ErasurePreflight preflight(String subject) {

        var blockers = memberships.membershipsFor(subject).stream().map(OrganizationMembership::id).map(
                organizationId -> OrganizationScope.runAs(organizationId, () -> blockerFor(organizationId, subject)))
                .flatMap(Optional::stream).toList();
        return new ErasurePreflight(blockers.isEmpty(), blockers);
    }

    /**
     * Erases the subject's account across every hub it belongs to. Re-checks the guards - the preflight only informs a
     * caller; this decides - and refuses if any still bite, in case something changed between the two calls.
     */
    public void erase(String subject) {

        var preflight = preflight(subject);
        if (!preflight.permitted()) {
            throw new ConflictException(BLOCKED_CODE, "Account cannot be erased: " + describe(preflight.blockers()));
        }

        var erasedAt = clock.instant();
        var hubIds = hubsOf(subject);
        hubIds.forEach(hubId -> OrganizationScope.runAs(hubId, () -> purge.eraseFrom(hubId, subject, erasedAt)));
        erasePersonRootedData(subject, erasedAt);
        try {
            identityAccounts.deleteAccount(subject);
        } catch (RuntimeException identityFailure) {
            // The mapped 502 response tells the caller their data is gone and only sign-out remains, but the
            // caller retrying is not a plan an operator can rely on: log the cause chain here, where the subject
            // and Organization count are already in scope, so an orphaned identity provider account is at least
            // visible.
            log.error(
                    "Account erasure for subject '{}' committed across {} Organization(s), but the "
                            + "identity-provider account deletion failed and needs a retry",
                    subject, hubIds.size(), identityFailure);
            throw new AccountErasureIncompleteException(subject, hubIds.size(), identityFailure);
        }
    }

    /**
     * The second isolation axis: rows rooted at the person rather than at any Organization, scoped on
     * {@code app.current_subject}. Deliberately outside the per-hub loop above, and deliberately unconditional. Such a
     * row outlives every hub, so erasing it once per membership would be wrong even where the loop reaches it - and the
     * loop does not reach it at all for somebody who belongs to no hub, whose person-rooted rows would otherwise
     * survive their own account erasure entirely.
     *
     * <p>
     * {@link SubjectScope#runAs} wraps {@link PersonPurge#eraseFrom}'s {@code @Transactional} boundary rather than
     * sitting inside it, because the GUC is chosen when the transaction acquires its connection. Without it every
     * statement matches zero rows - silently, and green under any test connected as the database owner. The fan-out
     * over every registered {@code PersonDataEraser} is delegated to {@link PersonPurge}, a separate bean, for the same
     * reason {@link #erase} delegates the per-Organization fan-out to {@link MemberPurge}: {@code @Transactional} on a
     * self-invoked method does nothing, because Spring's proxy is never on the call path. Each registered
     * {@code PersonDataEraser} carries its own transaction, and that delegation is what makes them one: a failure in
     * any of them rolls back every other's writes too.
     *
     * <p>
     * No {@code erasure_receipt} row is written here. That receipt is an Organization's own anonymous proof (V18 scopes
     * it on {@code app.current_org}) and this erasure belongs to no Organization, so the counts are logged instead of
     * being filed under a tenant they have nothing to do with.
     */
    private void erasePersonRootedData(String subject, Instant erasedAt) {

        var erasure = PersonErasure.builder().subject(subject).surrogateId(SurrogateIds.mint()).erasedAt(erasedAt)
                .build();
        var counts = SubjectScope.runAs(subject, () -> personPurge.eraseFrom(erasure));
        log.info("Person-rooted erasure removed {} row(s) and anonymised {} column(s)", counts.deleted(),
                counts.anonymised());
    }

    /** Must be called from inside {@code OrganizationScope.runAs(organizationId, ...)}; see the class javadoc. */
    private Optional<ErasureBlocker> blockerFor(String organizationId, String subject) {

        var organization = organizations.organization(organizationId);
        if (organization.isEmpty()) {
            // The identity provider still lists a membership the local registry no longer has a row for - nothing to
            // guard.
            return Optional.empty();
        }
        if (!memberDataRights.exercisedByMembers(organizationId)) {
            return Optional.of(new ErasureBlocker(BlockerKind.ENTERPRISE_MEMBERSHIP, organizationId, null));
        }
        if (roleBindings.isSoleOwner(organizationId, subject)) {
            var communityName = communities.hubCommunityName(organizationId).orElse(organization.get().name());
            return Optional.of(new ErasureBlocker(BlockerKind.SOLE_OWNER, organizationId, communityName));
        }
        return Optional.empty();
    }

    /**
     * The Organizations whose members exercise their own data rights (see {@link MemberDataRightsPolicy}), each asked
     * inside its own scope; see the class javadoc.
     */
    private List<String> hubsOf(String subject) {

        return memberships.membershipsFor(subject).stream().map(OrganizationMembership::id)
                .filter(organizationId -> OrganizationScope.runAs(organizationId,
                        () -> memberDataRights.exercisedByMembers(organizationId)))
                .toList();
    }

    /**
     * Prose for a stale {@code erase} call's refusal message, which reaches a log or an error body verbatim. The
     * {@code preflight} endpoint returns {@link ErasureBlocker} structured for the frontend to render; this is the one
     * place a blocker turns into a sentence, rather than {@code ErasureBlocker}'s record {@code toString()} leaking a
     * Java type name and field syntax into that message.
     */
    private static String describe(List<ErasureBlocker> blockers) {

        return blockers.stream().map(AccountErasure::describe).collect(Collectors.joining("; "));
    }

    private static String describe(ErasureBlocker blocker) {

        return blocker.communityName() == null
                ? "%s (%s)".formatted(blocker.kind(), blocker.organizationId())
                : "%s (%s, \"%s\")".formatted(blocker.kind(), blocker.organizationId(), blocker.communityName());
    }
}
