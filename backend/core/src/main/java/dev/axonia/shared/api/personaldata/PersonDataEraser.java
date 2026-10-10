package dev.axonia.shared.api.personaldata;

/**
 * Removes or anonymises everything one bounded context holds about one person, on the second isolation axis: rows
 * rooted at a person rather than at an Organization, scoped by the connection's {@code app.current_subject} rather than
 * its {@code app.current_org}.
 *
 * <p>
 * Separate from {@link MemberDataEraser} because the two cannot share an invocation. A member eraser is run once per
 * Organization the person belongs to, inside {@code OrganizationScope.runAs}, whose authentication names an
 * Organization and no subject - so a person-rooted table's RLS policy fails closed there and every statement would
 * quietly affect zero rows. A person eraser is run exactly once, inside {@code SubjectScope.runAs}, which wraps this
 * bean's {@code @Transactional} boundary so the GUC is in force when the transaction acquires its connection.
 *
 * <p>
 * Running once rather than once per Organization is not only a scoping detail: a person-rooted row outlives any one
 * hub, so erasing it once per membership would be wrong even if the scope allowed it.
 */
public interface PersonDataEraser {

    ErasureCounts erase(PersonErasure erasure);
}
