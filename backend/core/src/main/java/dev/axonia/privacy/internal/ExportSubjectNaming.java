package dev.axonia.privacy.internal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.axonia.Classification;
import dev.axonia.ExportFragment;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataFootprint;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;

/**
 * Applied once to a whole assembled package rather than in each of fourteen exporters. Every column any module
 * classified {@link Classification#SUBJECT} is dropped outright for a member - they already know who they are, and
 * their identity provider subject is the single most useful key in a file that leaves the building by design - and is
 * resolved to a display name and an address for a whole-Organization export, whose subject is the tenant and whose
 * reader is its controller.
 *
 * <p>
 * It lives here and not in the exporters because a rule fourteen modules have to remember is a rule that gets
 * forgotten: {@code bookmark.source_author_user_id} reached a member's export once already, on a row that was genuinely
 * theirs, with every test in the module green. A module that adds a subject column next year gets this treatment
 * without knowing this class exists.
 *
 * <p>
 * The whole package is resolved through one {@link UserDirectory#byIds} call, not one per row: a whole-Organization
 * export names every member of the tenant, and that port is already the widest read in the platform. It is total by
 * construction, so a subject it cannot resolve comes back named {@link DirectoryUser#DELETED_MEMBER_LABEL} rather than
 * blank, and an empty subject set never reaches it at all.
 *
 * <p>
 * A row may be left with no cells at all - {@code expertise_opt_out} carries nothing but the subject - and it survives
 * as an empty row. The row existing IS the fact being exported, so dropping it would lose one.
 */
record ExportSubjectNaming(Set<String> columns, Map<String, DirectoryUser> people, boolean namesPeople) {

    private static final Set<String> GENERIC_BASES = Set.of("", "user", "subject", "member");
    private static final String MEMBER = "member";
    private static final String SUBJECT_SUFFIX = "_user_id";
    private static final String ID_SUFFIX = "_id";
    private static final String EMAIL_SUFFIX = "_email";

    static List<ExportFragment> appliedTo(ExportScope scope, List<ExportFragment> fragments,
            List<PersonalDataFootprint> footprints, UserDirectory directory) {

        var naming = forScope(scope, fragments, footprints, directory);
        return fragments.stream().map(naming::named).toList();
    }

    private static ExportSubjectNaming forScope(ExportScope scope, List<ExportFragment> fragments,
            List<PersonalDataFootprint> footprints, UserDirectory directory) {

        var columns = subjectColumnsOf(footprints);
        if (!(scope instanceof ExportScope.WholeOrganization whole)) {
            return new ExportSubjectNaming(columns, Map.of(), false);
        }
        var subjects = subjectValuesIn(fragments, columns);
        if (subjects.isEmpty()) {
            return new ExportSubjectNaming(columns, Map.of(), true);
        }
        return new ExportSubjectNaming(columns, peopleByUserId(directory, whole.organizationId(), subjects), true);
    }

    private static Set<String> subjectColumnsOf(List<PersonalDataFootprint> footprints) {

        return footprints.stream().flatMap(footprint -> footprint.tables().stream())
                .flatMap(table -> table.exportedSubjectColumns().stream()).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, DirectoryUser> peopleByUserId(UserDirectory directory, String organizationId,
            List<String> userIds) {

        return directory.byIds(organizationId, userIds).stream()
                .collect(Collectors.toUnmodifiableMap(DirectoryUser::userId, Function.identity()));
    }

    /**
     * Malformed payloads pass straight through here and fail later, in {@link ExportIndexHtml}, where the diagnostic
     * names the fragment and the key that produced them. Failing twice, in two voices, for one cause would make the
     * worse message the one a real export fails with.
     */
    private static List<String> subjectValuesIn(List<ExportFragment> fragments, Set<String> columns) {

        var subjects = new LinkedHashSet<String>();
        fragments.forEach(fragment -> fragment.payload().values()
                .forEach(rows -> rowsOf(rows).forEach(row -> collectSubjectsOf(row, columns, subjects))));
        return List.copyOf(subjects);
    }

    private static List<?> rowsOf(Object rows) {

        return rows instanceof List<?> list ? list : List.of();
    }

    private static void collectSubjectsOf(Object row, Set<String> columns, Set<String> subjects) {

        if (row instanceof Map<?, ?> map) {
            map.forEach((column, value) -> {
                if (value != null && columns.contains(String.valueOf(column))) {
                    subjects.add(String.valueOf(value));
                }
            });
        }
    }

    /**
     * Derived from the column name by one rule so that no module ever chooses what its people are called: strip a
     * trailing {@code _user_id}, else a trailing {@code _id}, and call whatever is left {@code member} when it says
     * nothing beyond "a person". That settles every declared subject column without a table of exceptions, and settles
     * next year's the same way.
     */
    static String baseNameOf(String column) {

        var base = column.endsWith(SUBJECT_SUFFIX) ? without(column, SUBJECT_SUFFIX) : withoutId(column);
        return GENERIC_BASES.contains(base) ? MEMBER : base;
    }

    private static String withoutId(String column) {

        return column.endsWith(ID_SUFFIX) ? without(column, ID_SUFFIX) : column;
    }

    private static String without(String column, String suffix) {

        return column.substring(0, column.length() - suffix.length());
    }

    private ExportFragment named(ExportFragment fragment) {

        var payload = new LinkedHashMap<String, Object>();
        fragment.payload().forEach((table, rows) -> payload.put(table, namedRows(rows)));
        return new ExportFragment(fragment.fileName(), payload, fragment.mediaKeys());
    }

    private Object namedRows(Object rows) {

        return rows instanceof List<?> list ? list.stream().map(this::namedRow).toList() : rows;
    }

    private Object namedRow(Object row) {

        if (!(row instanceof Map<?, ?> map)) {
            return row;
        }
        var named = new LinkedHashMap<String, Object>();
        var real = map.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        map.forEach((column, value) -> name(named, String.valueOf(column), value, real));
        return named;
    }

    private void name(Map<String, Object> row, String column, Object value, Set<String> real) {

        if (!columns.contains(column)) {
            row.put(column, value);
            return;
        }
        if (!namesPeople) {
            return;
        }
        var base = baseNameOf(column);
        var displaced = real.stream().filter(name -> !name.equals(column)).collect(Collectors.toUnmodifiableSet());
        var person = Optional.ofNullable(value).map(String::valueOf).map(people::get);
        derive(row, displaced, base, person.map(DirectoryUser::displayName).orElse(null));
        derive(row, displaced, base + EMAIL_SUFFIX, person.map(DirectoryUser::email).orElse(null));
    }

    /**
     * A derived name never displaces a real column - except the subject column's own, which is the one it is replacing.
     * A subject named {@code created_by} ({@code community_invite}) derives the base name {@code created_by} too,
     * identical to the column itself; refusing that overwrite would drop the person's name from the row altogether
     * rather than protect anything, so the subject column's own key is excluded from {@code displaced} before this
     * runs.
     *
     * <p>
     * The guard keeps a subject column from silently overwriting another real column's fact, or a label an exporter's
     * own join produced, when a real column shares a <em>different</em> subject's derived base name or its
     * {@code _email} sibling. No real table exercises this collision; {@code ExportPackageWriterTest} holds it with a
     * synthetic one.
     */
    private static void derive(Map<String, Object> row, Set<String> real, String name, Object value) {

        if (!real.contains(name)) {
            row.put(name, value);
        }
    }
}
