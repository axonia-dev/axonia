package dev.axonia.shared.api.personaldata;

import java.util.Optional;

/**
 * What an export covers: one Organization entire, one member within it, or one person apart from any Organization.
 *
 * <p>
 * Sealed, and with no Organization on the root type, so that every {@link PersonalDataExporter} has to say what it
 * answers for a {@link Person}: a module whose tables are rooted at an Organization cannot even name one to query
 * without first matching {@link InOrganization}.
 */
public sealed interface ExportScope {

    /** The Organization this export is read inside, or empty for a {@link Person}. */
    default Optional<String> organization() {

        return this instanceof InOrganization scope ? Optional.of(scope.organizationId()) : Optional.empty();
    }

    /** The one person whose own data this export is, or empty for a {@link WholeOrganization}. */
    default Optional<String> person() {

        return switch (this) {
            case WholeOrganization _ -> Optional.empty();
            case OneMember member -> Optional.of(member.userId());
            case Person person -> Optional.of(person.subject());
        };
    }

    /** The two scopes rooted at an Organization, which is what every Organization-rooted table is read by. */
    sealed interface InOrganization extends ExportScope {

        String organizationId();
    }

    record WholeOrganization(String organizationId) implements InOrganization {
    }

    record OneMember(String organizationId, String userId) implements InOrganization {
    }

    /**
     * One person, when the request names no Organization: somebody who signed in and joined nothing, or who has left
     * every hub. Only data rooted at the person answers it - their account, their standing, their onboarding - because
     * that is the only data axonia can hold about them without an Organization to hold it in.
     */
    record Person(String subject) implements ExportScope {
    }
}
