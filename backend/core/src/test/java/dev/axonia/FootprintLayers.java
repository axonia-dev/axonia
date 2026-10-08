package dev.axonia;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layers 1 to 3 of the data-subject-rights gate over one set of footprints, so the open and the Cloud halves run the
 * same checks over their own modules.
 */
public final class FootprintLayers {

    private static final Pattern IDENTIFIER = Pattern.compile("^(id|version|organization_id|.*_id)$");

    /**
     * {@code directory_group_member}'s own subject column is a SCIM id, which is {@link ExportTreatment#WITHHELD} and
     * which {@code UserDirectory.byIds} cannot resolve, so the identity exporter joins {@code identity_user} and emits
     * the canonical subject under the canonical name for the package writer to relabel like any other.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of("directory_group_member", Set.of("user_id"));

    private final NamedParameterJdbcTemplate jdbc;

    private final List<PersonalDataFootprint> footprints;

    private final List<PersonalDataExporter> exporters;

    public FootprintLayers(NamedParameterJdbcTemplate jdbc, List<PersonalDataFootprint> footprints,
            List<PersonalDataExporter> exporters) {

        this.jdbc = jdbc;
        this.footprints = footprints;
        this.exporters = exporters;
    }

    public Map<String, Set<String>> exportedIdentifiers() {

        var offenders = new TreeMap<String, Set<String>>();
        tables().forEach(table -> {
            var named = Stream.concat(table.exportedContext().stream(), table.exportedLabels().stream())
                    .filter(column -> IDENTIFIER.matcher(column).matches())
                    .filter(column -> !ALLOWED.getOrDefault(table.table(), Set.of()).contains(column))
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!named.isEmpty()) {
                offenders.put(table.table(), named);
            }
        });
        return offenders;
    }

    public List<String> withheldColumns() {

        return tables().flatMap(table -> table.personal().stream()
                .filter(column -> column.exportTreatment() == ExportTreatment.WITHHELD)
                .map(column -> table.table() + "." + column.column())).toList();
    }

    public Set<String> claimedTables() {

        return tables().map(TableFootprint::table).collect(Collectors.toCollection(TreeSet::new));
    }

    public List<String> tablesClaimedTwice() {

        return tables().map(TableFootprint::table).collect(Collectors.groupingBy(table -> table, Collectors.counting()))
                .entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).toList();
    }

    public Set<String> liveTables(Set<String> excluded) {

        return Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_type = 'BASE TABLE' AND table_name NOT IN (:excluded)",
                Map.of("excluded", excluded), String.class));
    }

    public void assertEveryColumnIsDeclared() {

        tables().forEach(footprint -> {
            var live = Set.copyOf(jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = :table",
                    Map.of("table", footprint.table()), String.class));
            var declared = footprint.declaredColumns();
            assertThat(declared).describedAs("columns of '%s' that are declared but no longer exist", footprint.table())
                    .isSubsetOf(live);
            assertThat(live).describedAs("columns of '%s' with no declaration - classify each as personal with a "
                    + "disposition, or list it under notPersonal", footprint.table()).isSubsetOf(declared);
        });
    }

    public void assertEveryPersonalColumnIsExported() {

        var exportersByPackage = exportersByPackage();
        footprints.forEach(footprint -> {
            var requiredColumns = footprint.tables().stream().flatMap(table -> table.personal().stream())
                    .filter(ColumnFootprint::reachesAnExport).map(ColumnFootprint::column).collect(Collectors.toSet());
            if (requiredColumns.isEmpty()) {
                return;
            }
            var modulePackage = footprint.getClass().getPackageName();
            var exporter = exportersByPackage.get(modulePackage);
            assertThat(exporter).describedAs("module '%s' declares personal columns %s but registers no matching "
                    + "PersonalDataExporter in its footprint test support", modulePackage, requiredColumns).isNotNull();
            var emittedKeys = keysEmittedBy(exporter);
            var missing = new TreeSet<>(requiredColumns);
            missing.removeAll(emittedKeys);
            assertThat(emittedKeys).describedAs("module '%s' export fragment is missing keys for personal columns %s",
                    modulePackage, missing).containsAll(requiredColumns);
        });
    }

    public void assertNoExportCarriesAWithheldColumn() {

        var exportersByPackage = exportersByPackage();
        footprints.forEach(footprint -> {
            var exporter = exportersByPackage.get(footprint.getClass().getPackageName());
            if (exporter == null) {
                return;
            }
            var permitted = footprint.tables().stream()
                    .flatMap(table -> Stream.concat(table.exportedColumns().stream(), table.exportedLabels().stream()))
                    .collect(Collectors.toSet());
            assertThat(keysEmittedBy(exporter))
                    .describedAs("module '%s' export fragment carries columns its footprint withholds",
                            footprint.getClass().getPackageName())
                    .isSubsetOf(permitted);
        });
    }

    private Stream<TableFootprint> tables() {

        return footprints.stream().flatMap(footprint -> footprint.tables().stream());
    }

    private Map<String, PersonalDataExporter> exportersByPackage() {

        return exporters.stream()
                .collect(Collectors.toMap(exporter -> exporter.getClass().getPackageName(), exporter -> exporter));
    }

    /**
     * Both scopes, unioned. A module whose tables carry an {@code organization_id} answers a whole-Organization export
     * with every row it holds, so probing that scope alone used to be enough. The person axis is not like that: a
     * module rooted at a person belongs to no Organization, so its exporter answers a whole-Organization request with
     * nothing at all - correctly, since handing a tenant's administrator somebody's own purchase would answer the
     * tenant's request with another person's data. Probing only that scope would let a person-rooted module satisfy
     * layers 3 and 3b by exporting nothing whatsoever.
     *
     * <p>
     * Unioning rather than replacing keeps both halves honest. The member-scoped projection is a subset of the
     * Organization-scoped one by construction ({@link ExportQueries} drops other people's {@code SUBJECT} columns for a
     * member), so the withholding assertion is no weaker for the modules that answer both.
     */
    private Set<String> keysEmittedBy(PersonalDataExporter exporter) {

        return Stream
                .of(exporter.export(new ExportScope.WholeOrganization(OpenSeed.ORG)),
                        exporter.export(new ExportScope.OneMember(OpenSeed.ORG, OpenSeed.USER)))
                .flatMap(fragment -> fragment.payload().values().stream()).flatMap(value -> ((List<?>) value).stream())
                .map(row -> (Map<?, ?>) row).flatMap(row -> row.keySet().stream()).map(String.class::cast)
                .collect(Collectors.toSet());
    }
}
