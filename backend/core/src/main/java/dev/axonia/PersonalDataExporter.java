package dev.axonia;

/**
 * What one bounded context contributes to a data-subject export, at every scope {@link ExportScope} offers. Each module
 * owning personal-data tables implements this inside its {@code internal} package, next to its
 * {@link PersonalDataFootprint} and, where it has one, its {@link MemberDataEraser}. A module writes one query family
 * per table with an optional predicate that only applies for {@link ExportScope.OneMember}, so the member export and
 * the Organization export cost one implementation each rather than two. A module whose tables are rooted at an
 * Organization answers a {@link ExportScope.Person} with {@link ExportFragment#nothing}, and a module rooted at the
 * person answers it exactly as it answers that person's member export.
 */
public interface PersonalDataExporter {

    ExportFragment export(ExportScope scope);
}
