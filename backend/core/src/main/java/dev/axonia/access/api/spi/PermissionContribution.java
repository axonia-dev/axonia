package dev.axonia.access.api.spi;

import java.util.Set;

import dev.axonia.access.api.Permission;

/**
 * The permissions a module declares beside the code that checks them, outside {@link Permission#OPEN}. A contribution
 * is a stateless Spring component; {@code KnownPermissions} collects every one the running edition has.
 */
public interface PermissionContribution {

    Set<Permission> permissions();
}
