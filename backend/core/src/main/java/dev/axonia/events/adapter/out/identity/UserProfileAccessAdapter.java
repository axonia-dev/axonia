package dev.axonia.events.adapter.out.identity;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.identity.api.MemberProfiles;
import dev.axonia.identity.api.UserDirectory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class UserProfileAccessAdapter implements UserProfileAccess {

    private final MemberProfiles userService;

    /**
     * An id with no row left in identity_user - an erased member's surrogate id, most often - falls back to the neutral
     * "Deleted member" label, never the raw id. Before this fix an erased topic proposer's surrogate id (e.g.
     * {@code erased:<uuid>}) rendered verbatim in the backlog.
     */
    @Override
    public String displayName(String organizationId, String userId) {

        return userService.displayName(organizationId, userId).orElse(UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL);
    }

    @Override
    public Map<String, String> displayNames(String organizationId, Collection<String> userIds) {

        var found = userService.displayNames(organizationId, userIds);
        return userIds.stream().distinct().collect(Collectors.toMap(id -> id,
                id -> found.getOrDefault(id, UserDirectory.DirectoryUser.DELETED_MEMBER_LABEL)));
    }

    /** Deliberately no fill-in of the missing ids that {@link #displayNames} performs: see the port. */
    @Override
    public Map<String, String> timeZones(String organizationId, Collection<String> userIds) {

        return userService.timeZones(organizationId, userIds);
    }
}
