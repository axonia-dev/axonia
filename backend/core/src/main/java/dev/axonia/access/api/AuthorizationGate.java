package dev.axonia.access.api;

import java.util.Set;

public interface AuthorizationGate {

    /** Throws ForbiddenException if the current caller lacks the permission in the current Organization. */
    void requirePermission(Permission permission);

    boolean hasPermission(Permission permission);

    /**
     * Effective permissions of the current caller in the current Organization: backs {@code /access/me} and the
     * privilege ceiling that gates handing out or taking away a role's authority.
     */
    Set<Permission> currentPermissions();

    /**
     * Role ids effectively assigned to the current caller in the current Organization: backs the self-lockout guard and
     * the roles a member holds.
     */
    Set<Long> currentRoleIds();
}
