package dev.axonia.identity.application.scim;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.spi.DirectoryGroupClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether a directory group arriving over SCIM is confirmed on the spot or held.
 *
 * <p>
 * The SCIM client chooses its own {@code externalId} and a {@code GROUP} role binding matches on exactly that value, so
 * a push under a key somebody already bound would otherwise be a way into whatever role that binding grants. The group
 * is still created: refusing it would break the ordinary "bind first, provision second" onboarding order, and an
 * administrator who genuinely meant this group can release it with one {@code role:assign} confirmation. Until then it
 * confers nothing.
 *
 * <p>
 * A separate collaborator rather than two private methods on {@link ScimGroupService}, because the decision is a policy
 * of its own: it is the only part of provisioning that reads authorization state, and it is the part a reader has to be
 * able to find.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class DirectoryGroupHold {

    private final DirectoryGroupClaims directoryGroupClaims;
    private final Clock clock;

    /** When a group arriving under {@code reconciliationKey} counts as confirmed, or null while it is held. */
    Instant stampFor(String organizationId, String reconciliationKey) {

        if (directoryGroupClaims.isClaimedByARoleBinding(organizationId, reconciliationKey)) {
            log.warn("Directory group held unconfirmed in Organization {}: a role binding already waits on key {}",
                    organizationId, reconciliationKey);
            return null;
        }
        return clock.instant();
    }

    /**
     * The confirmation a renamed group carries. A rename that leaves the reconciliation key alone changes nothing about
     * which bindings match, so the group keeps whatever standing it had. A rename that moves the group onto a new key -
     * the non-Entra arm, where the key follows {@code displayName} - is a fresh claim on that key and is held exactly
     * like a create would be, otherwise the guard would be one {@code PATCH} away from being bypassed.
     */
    Instant afterRekey(String organizationId, DirectoryGroupRekey rekey) {

        if (rekey.newKey().equals(rekey.previousKey())) {
            return rekey.previousStamp();
        }
        return stampFor(organizationId, rekey.newKey());
    }

    /** What a rename does to a group's reconciliation key, and the standing it held under the old one. */
    record DirectoryGroupRekey(String previousKey, Instant previousStamp, String newKey) {
    }
}
