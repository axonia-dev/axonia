package dev.axonia.access;

import java.util.Set;

/**
 * The permissions a module declares beside the code that checks them, outside {@link Permission#OPEN}. A contribution
 * is a stateless Spring component; {@link KnownPermissions} collects every one the running edition has.
 */
public interface PermissionContribution {

    Set<Permission> permissions();
}
