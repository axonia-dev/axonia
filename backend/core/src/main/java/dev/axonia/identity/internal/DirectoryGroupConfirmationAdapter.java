package dev.axonia.identity.internal;

import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.DirectoryGroupConfirmation;
import lombok.RequiredArgsConstructor;

/** Reads and stamps {@code directory_group.confirmed_at} to satisfy the {@link DirectoryGroupConfirmation} port. */
@Component
@RequiredArgsConstructor
class DirectoryGroupConfirmationAdapter implements DirectoryGroupConfirmation {

    private final DirectoryGroupRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Set<String> keysAwaitingConfirmation(String organizationId) {

        return repository.findByOrganizationIdAndConfirmedAtIsNull(organizationId).stream()
                .map(DirectoryGroupEntity::getReconciliationKey).filter(key -> key != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public boolean confirm(String organizationId, String reconciliationKey) {

        return repository.findByOrganizationIdAndReconciliationKey(organizationId, reconciliationKey)
                .map(this::stampUnlessAlreadyConfirmed).orElse(false);
    }

    /**
     * Confirming a group that is already confirmed is a no-op rather than a re-stamp: {@code confirmed_at} records when
     * an administrator vouched for the group, and a second click of the same button is not a second vouching.
     */
    private boolean stampUnlessAlreadyConfirmed(DirectoryGroupEntity group) {

        if (group.getConfirmedAt() == null) {
            repository.save(group.toBuilder().confirmedAt(clock.instant()).build());
        }
        return true;
    }
}
