package dev.axonia;

/**
 * Removes or anonymises everything one bounded context holds about one member of one Organization. Each module owning
 * member-bearing tables implements this inside its {@code internal} package, next to its {@link OrganizationDataPurger}
 * and declaring the same tables its {@link PersonalDataFootprint} does. The privacy context injects the full list and
 * invokes it inside a single per-Organization transaction. Implementations rely on the caller's RLS scope (the
 * connection's {@code app.current_org}) as the outer isolation guarantee, and express cross-module FK ordering with
 * {@code @Order} exactly as the purgers do.
 */
public interface MemberDataEraser {

    ErasureCounts erase(MemberErasure erasure);
}
