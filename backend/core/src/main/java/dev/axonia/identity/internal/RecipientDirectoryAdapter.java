package dev.axonia.identity.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.communication.RecipientDirectory;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserService;
import lombok.RequiredArgsConstructor;

/**
 * Satisfies {@code communication}'s {@link RecipientDirectory} over this module's {@link UserDirectory}.
 *
 * <p>
 * The adapter lives here for the reason {@code events.MessageSubjectPolicyAdapter} gives about its own direction:
 * {@code identity} already depends on {@code communication} to enqueue, so an adapter here adds no edge that
 * {@code communication} does not already accept on its own terms, while the reverse import would be the cycle this
 * whole arrangement exists to remove.
 */
@Component
@RequiredArgsConstructor
class RecipientDirectoryAdapter implements RecipientDirectory {

    private final UserDirectory userDirectory;
    private final UserService userService;

    /**
     * The clock is resolved in one batched read beside the names rather than per recipient. A member with none is
     * absent from that map, and the null passes straight through: every reader downstream distinguishes unknown from
     * measured, and filling it in here would remove the only place that distinction is still visible.
     */
    @Override
    public List<Recipient> recipientsOf(String organizationId, List<String> userIds) {

        var zones = userService.timeZones(organizationId, userIds);
        return userDirectory.byIds(organizationId, userIds).stream().map(user -> new Recipient(user.userId(),
                user.displayName(), user.email(), presenceOf(user.status()), zones.get(user.userId()))).toList();
    }

    private static Presence presenceOf(UserDirectory.MemberStatus status) {

        return switch (status) {
            case PRESENT -> Presence.PRESENT;
            case DEACTIVATED -> Presence.DEACTIVATED;
            case ERASED -> Presence.ERASED;
        };
    }
}
