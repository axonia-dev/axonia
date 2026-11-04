package dev.axonia.identity.application.scim;

import dev.axonia.identity.api.spi.DirectorySubjects;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import lombok.extern.slf4j.Slf4j;

/**
 * What a directory's key says about the person it is filed under: no other row may hold it, and when it is the subject
 * they will sign in with, a person who holds none yet takes it.
 *
 * <p>
 * A subject is never moved. A person who already holds one keeps it, and a subject somebody else already holds stays
 * with them: that is a person whose first sign-in did not find their pushed row by address, so the two rows are one
 * person, and merging them is not this push's to decide.
 */
@Slf4j
final class DirectorySubjectClaim {

    private final DirectorySubjects directorySubjects;
    private final UserStore userStore;

    DirectorySubjectClaim(DirectorySubjects directorySubjects, UserStore userStore) {

        this.directorySubjects = directorySubjects;
        this.userStore = userStore;
    }

    /** Refuses a key another row is filed under, with the conflict a provisioning client knows to reconcile. */
    void requireUnheld(String organizationId, UserAccount account) {

        if (account.externalId() == null) {
            return;
        }
        userStore.findByOrganizationIdAndExternalId(organizationId, account.externalId())
                .filter(holder -> !holder.id().equals(account.id())).ifPresent(holder -> {
                    throw ScimException
                            .uniquenessConflict("User with externalId already exists: " + account.externalId());
                });
    }

    UserAccount withSubject(String organizationId, UserAccount account) {

        if (account.userId() != null) {
            return account;
        }
        return directorySubjects.subjectOf(account.externalId())
                .filter(subject -> isFree(organizationId, subject, account))
                .map(subject -> account.toBuilder().userId(subject).build()).orElse(account);
    }

    private boolean isFree(String organizationId, String subject, UserAccount account) {

        var holder = userStore.findByOrganizationIdAndUserId(organizationId, subject);
        holder.ifPresent(other -> log.warn("Directory row {} names the subject identity row {} already holds; it stays"
                + " without one, and the two rows are the same person", account.scimId(), other.id()));
        return holder.isEmpty();
    }
}
