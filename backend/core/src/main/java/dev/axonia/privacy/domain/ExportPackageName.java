package dev.axonia.privacy.domain;

import java.time.LocalDate;
import java.util.Optional;

import dev.axonia.shared.api.personaldata.ExportScope;
import lombok.experimental.UtilityClass;

/**
 * The one name an export package answers to: the {@code .zip} the browser saves, and the single root folder every entry
 * inside it sits under. Both are derived here so they cannot drift apart.
 *
 * <p>
 * The root folder is what stops two extracted exports overwriting each other. Without it, every archive puts
 * {@code identity.json} and a raw media key such as {@code avatars/acme/alice.jpg} straight at the archive root, so
 * unzipping a second export into the same directory silently replaces the first.
 *
 * <p>
 * The label is the one {@code ExportLabels} gives the Organization, then its name, then the word {@code organization} -
 * never its identifier. A person who downloads two archives should be able to tell which Organization each came from,
 * and a tenant identifier tells them nothing while being a live key into axonia's own systems, printed on the outside
 * of a file that leaves the building by design.
 *
 * <p>
 * Whichever rung answers is reduced to {@code [A-Za-z0-9-]}. That is not cosmetic: the name is interpolated into a
 * {@code Content-Disposition} header, and a value carrying a quote or a newline is a response-splitting primitive
 * rather than a filename. A rung whose reduction leaves nothing usable is treated as no answer at all and falls
 * through, so an Organization named only in a non-Latin script gets the generic word rather than a row of dashes.
 */
@UtilityClass
public class ExportPackageName {

    private static final String FALLBACK = "organization";
    private static final String PERSONAL = "personal";

    /**
     * The name for {@code scope}'s package, given the label and the name of the Organization it is read inside where it
     * has one. A {@link ExportScope.Person}'s package is about no Organization, so it is called {@code personal} rather
     * than falling through to the word for an Organization nobody could find.
     */
    public String forScope(ExportScope scope, Optional<String> label, Optional<String> organizationName,
            LocalDate date) {

        if (scope instanceof ExportScope.Person) {
            return "axonia-export-%s-%s".formatted(PERSONAL, date);
        }
        return of(label.orElse(null), organizationName.orElse(null), date);
    }

    public String of(String label, String organizationName, LocalDate date) {

        return "axonia-export-%s-%s".formatted(labelOf(label, organizationName), date);
    }

    private String labelOf(String label, String organizationName) {

        return reduced(label).or(() -> reduced(organizationName)).orElse(FALLBACK);
    }

    private Optional<String> reduced(String candidate) {

        if (candidate == null) {
            return Optional.empty();
        }
        var safe = candidate.replaceAll("[^A-Za-z0-9-]", "-").replaceAll("-{2,}", "-");
        return safe.isBlank() || safe.equals("-") ? Optional.empty() : Optional.of(safe);
    }
}
