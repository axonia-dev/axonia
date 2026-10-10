package dev.axonia.shared.api.personaldata;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Builder;

/**
 * One table's personal-data declaration: which {@link DataCategory} of the privacy notice it belongs under, the columns
 * that hold personal data, with structure, and the remaining columns as a flat affirmation that somebody looked at them
 * and they do not. The two column buckets together must equal the table's live column set, which is what makes a newly
 * added column a build failure rather than an oversight.
 *
 * <p>
 * {@link #exportedContext} is the third, narrower bucket: the non-personal columns a data-subject export actually
 * carries, so that {@link #exportedColumns()} can name every column of the projection instead of leaving an exporter to
 * write {@code SELECT *}. It is deliberately an opt-in subset of {@link #notPersonal} rather than a denylist of the
 * columns to withhold: a column added next year lands in {@code notPersonal} to satisfy
 * {@code PersonalDataFootprintTest} and is excluded from every export until somebody separately decides it belongs
 * there. A denylist over, say, {@code *_hash} would cover today's {@code token_hash} and silently ship the first column
 * named {@code recovery_secret}.
 *
 * <p>
 * {@link #exportedLabels} is a fourth bucket, disjoint from every column name on the table: the aliases an exporter may
 * emit in place of a raw key, such as a community's name instead of its id. A personal column can also stay out of a
 * projection a third way, beside {@link Disposition#RETAIN} and simply never being opted into {@code exportedContext}:
 * {@link ExportTreatment#WITHHELD} marks a column that is squarely the member's own, erased with them, and still never
 * printed, because it is a key into a system rather than a fact about a person, or a snapshot of the member on an
 * operator-side record.
 */
@Builder
public record TableFootprint(String table, DataCategory category, List<ColumnFootprint> personal,
        Set<String> notPersonal, Set<String> exportedContext, Set<String> exportedLabels) {

    public TableFootprint {
        Objects.requireNonNull(category, "'" + table + "' declares no DataCategory");
        exportedContext = exportedContext == null ? Set.of() : exportedContext;
        exportedLabels = exportedLabels == null ? Set.of() : exportedLabels;
        var overlap = personal.stream().map(ColumnFootprint::column).filter(notPersonal::contains)
                .collect(Collectors.toSet());
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "Columns declared both personal and not personal on '" + table + "': " + overlap);
        }
        var undeclared = exportedContext.stream().filter(column -> !notPersonal.contains(column))
                .collect(Collectors.toSet());
        if (!undeclared.isEmpty()) {
            throw new IllegalArgumentException("Exported context columns of '" + table
                    + "' that are not declared under notPersonal: " + undeclared);
        }
        var realColumns = Stream.concat(personal.stream().map(ColumnFootprint::column), notPersonal.stream())
                .collect(Collectors.toSet());
        var collides = exportedLabels.stream().filter(realColumns::contains).collect(Collectors.toSet());
        if (!collides.isEmpty()) {
            throw new IllegalArgumentException("Exported labels of '" + table
                    + "' that shadow a real column - name the label for what it reads, not for the key it "
                    + "replaces: " + collides);
        }
    }

    public Set<String> declaredColumns() {

        return Stream.concat(personal.stream().map(ColumnFootprint::column), notPersonal.stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The columns an export of this table may carry, sorted so the projection is stable: every personal column except
     * the ones {@link Disposition#RETAIN} keeps outside the data-subject regime entirely (an operator's identity on a
     * tenant's row is another identified person's data, and Art. 15(4) is explicit that answering one person's access
     * request must not hand over another's) and the ones {@link ExportTreatment#WITHHELD} keeps out as a key into a
     * system or a snapshot on an operator-side record - the columns {@link ColumnFootprint#reachesAnExport()} answers
     * for - plus the non-personal columns explicitly opted in as context. Everything else - credential digests above
     * all - is absent because nobody said it belonged.
     *
     * <p>
     * A column may be declared twice under different dispositions ({@code action_item.assignee_user_id} is both
     * {@code CLEAR} and {@code ANONYMISE}, depending on the item's status), so the personal side is de-duplicated
     * rather than assumed distinct.
     */
    public List<String> exportedColumns() {

        return Stream.concat(personal.stream().filter(ColumnFootprint::reachesAnExport).map(ColumnFootprint::column),
                exportedContext.stream()).distinct().sorted().toList();
    }

    /** The subset of {@link #exportedColumns()} classified as {@link Classification#SUBJECT}: identifiers of people. */
    public Set<String> exportedSubjectColumns() {

        var exported = Set.copyOf(exportedColumns());
        return personal.stream().filter(column -> column.classification() == Classification.SUBJECT)
                .map(ColumnFootprint::column).filter(exported::contains).collect(Collectors.toUnmodifiableSet());
    }
}
