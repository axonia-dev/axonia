package dev.axonia.identity.internal;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.health.PresenceSignals;
import dev.axonia.identity.UserDirectory;
import lombok.RequiredArgsConstructor;

/**
 * Identity's implementation of the community-health presence signal port. Delegates directly to
 * {@link UserDirectory#presentOf}, which is already scoped to live, active {@code identity_user} rows.
 */
@Component
@RequiredArgsConstructor
class PresenceSignalsAdapter implements PresenceSignals {

    private final UserDirectory userDirectory;

    @Override
    public Set<String> presentOf(String organizationId, Collection<String> userIds) {

        return userDirectory.presentOf(organizationId, userIds);
    }
}
