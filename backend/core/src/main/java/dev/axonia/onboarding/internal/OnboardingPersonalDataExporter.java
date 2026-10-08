package dev.axonia.onboarding.internal;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.ExportFragment;
import dev.axonia.ExportQueries;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataExporter;
import dev.axonia.SubjectScope;
import dev.axonia.TableFootprint;
import lombok.RequiredArgsConstructor;

/**
 * A person's own path and the steps it froze at assignment.
 *
 * <p>
 * One person's only: a member's export and a {@link ExportScope.Person}'s carry it alike. A whole-Organization export
 * gets two empty lists, because a path belongs to no Organization: it is rooted at a person and outlives any one hub,
 * so handing it to a tenant's administrator would answer the tenant's request with somebody else's data. The keys are
 * still present so the fragment says plainly that the module was asked and holds nothing at that scope.
 *
 * <p>
 * The reads run inside {@code SubjectScope.runAs}, because V99 scopes both {@code onboarding_path} and
 * {@code onboarding_step} on {@code app.current_subject} for reads as well as writes, and {@code ExportPackageWriter}
 * enters an Organization scope rather than a person's. That wrap is legal exactly here: the writer runs every exporter
 * outside any transaction, by documented design, so each query below acquires its own connection and picks up the GUC.
 *
 * <p>
 * Every projection is named column by column from {@link TableFootprint#exportedColumns()}. A {@code SELECT *} would
 * fail the export-leak layer by construction.
 */
@Component
@RequiredArgsConstructor
public class OnboardingPersonalDataExporter implements PersonalDataExporter {

    private static final String FILE = "onboarding.json";

    private final NamedParameterJdbcTemplate jdbc;
    private final OnboardingPersonalDataFootprint footprint;

    @Override
    public ExportFragment export(ExportScope scope) {

        var person = scope.person();
        if (person.isEmpty()) {
            return fragment(List.of(), List.of());
        }
        var subject = person.get();
        return SubjectScope.runAs(subject,
                () -> fragment(rows(OnboardingPersonalDataFootprint.ONBOARDING_PATH, scope, subject),
                        rows(OnboardingPersonalDataFootprint.ONBOARDING_STEP, scope, subject)));
    }

    private ExportFragment fragment(List<Map<String, Object>> path, List<Map<String, Object>> steps) {

        return new ExportFragment(FILE, Map.of(OnboardingPersonalDataFootprint.ONBOARDING_PATH, path,
                OnboardingPersonalDataFootprint.ONBOARDING_STEP, steps), List.of());
    }

    private List<Map<String, Object>> rows(String table, ExportScope scope, String subject) {

        var columns = ExportQueries.columnList(footprint.table(table), scope, OnboardingPersonalDataFootprint.SUBJECT);
        return jdbc.queryForList("SELECT " + columns + " FROM " + table + " WHERE subject = :subject",
                Map.of("subject", subject));
    }
}
