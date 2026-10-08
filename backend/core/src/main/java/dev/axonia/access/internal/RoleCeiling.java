package dev.axonia.access.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.ForbiddenException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.KnownPermissions;
import dev.axonia.access.Permission;
import lombok.RequiredArgsConstructor;

/**
 * The privilege ceiling: an administrator may only act on authority they themselves already hold.
 *
 * <p>
 * Handing out a role whose permission set exceeds the caller's own would let a delegated role-admin self-escalate to
 * Owner. Taking one away is refused on the same ceiling, because removing a role nobody else can restore is the same
 * power over somebody above the caller: a delegated role admin holding only {@code role:read} and {@code role:assign}
 * could otherwise strip the Owner's binding and take ownership transfer, organization deletion and SCIM off them, while
 * their own binding still granted {@code role:assign}.
 *
 * <p>
 * One definition, shared by every operation that moves authority - binding a role, revoking one, and confirming the
 * directory group a binding is waiting on - so that a new one cannot quietly arrive without it.
 */
@Component
@RequiredArgsConstructor
public class RoleCeiling {

    private final AuthorizationGate gate;
    private final KnownPermissions knownPermissions;

    /** Refuses handing {@code role} out when it grants more than the caller holds. */
    public void guardAssignable(RoleEntity role) {

        var missing = permissionsBeyondCaller(role);
        if (!missing.isEmpty()) {
            throw new ForbiddenException(
                    "A role can only be assigned if every permission it grants is already held; missing: "
                            + String.join(", ", missing));
        }
    }

    /** Refuses taking {@code role} away when it grants more than the caller holds. */
    public void guardRevocable(RoleEntity role) {

        var missing = permissionsBeyondCaller(role);
        if (!missing.isEmpty()) {
            throw new ForbiddenException(
                    "A role can only be revoked if every permission it grants is already held; missing: "
                            + String.join(", ", missing));
        }
    }

    /**
     * The permission codes on {@code role} that the caller does not currently hold, sorted for a stable message. Both
     * guards share this computation: only the message naming which direction was refused differs between them.
     */
    private List<String> permissionsBeyondCaller(RoleEntity role) {

        var held = gate.currentPermissions();
        return role.getPermissions().stream().flatMap(p -> knownPermissions.byCode(p.getPermission()).stream())
                .filter(p -> !held.contains(p)).map(Permission::code).sorted().toList();
    }
}
