package dev.axonia.identity.application;

import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.DirectoryGroupConfirmation;
import dev.axonia.identity.application.port.out.DirectoryGroupStore;
import dev.axonia.identity.domain.DirectoryGroup;
import lombok.RequiredArgsConstructor;

/** Reads and stamps {@code directory_group.confirmed_at} to satisfy the {@link DirectoryGroupConfirmation} port. */
@Component
@RequiredArgsConstructor
class DirectoryGroupConfirmationAdapter implements DirectoryGroupConfirmation {

    private final DirectoryGroupStore store;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Set<String> keysAwaitingConfirmation(String organizationId) {

        return store.findByOrganizationIdAndConfirmedAtIsNull(organizationId).stream()
                .map(DirectoryGroup::reconciliationKey).filter(key -> key != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public boolean confirm(String organizationId, String reconciliationKey) {

        return store.findByOrganizationIdAndReconciliationKey(organizationId, reconciliationKey)
                .map(this::stampUnlessAlreadyConfirmed).orElse(false);
    }

    /**
     * Confirming a group that is already confirmed is a no-op rather than a re-stamp: {@code confirmed_at} records when
     * an administrator vouched for the group, and a second click of the same button is not a second vouching.
     */
    private boolean stampUnlessAlreadyConfirmed(DirectoryGroup group) {

        if (group.confirmedAt() == null) {
            store.save(group.toBuilder().confirmedAt(clock.instant()).build());
        }
        return true;
    }
}
