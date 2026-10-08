package dev.axonia.access;

/**
 * A role bound to the caller, by id and name. The id travels because the profile surface tints a role by it, so the
 * same role reads in the same colour there as on the admin roster. It carries no permissions: what the caller's roles
 * grant is already answered, in one list, by {@code /access/me}'s permissions.
 */
public record MyRole(Long id, String name) {
}
