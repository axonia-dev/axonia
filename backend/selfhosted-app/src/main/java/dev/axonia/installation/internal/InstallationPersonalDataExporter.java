package dev.axonia.installation.internal;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import dev.axonia.ExportFragment;
import dev.axonia.ExportQueries;
import dev.axonia.ExportScope;
import dev.axonia.PersonalDataExporter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** A person's changes to the installation's settings; a whole-Organization export, the installation's, carries all. */
@Component
@RequiredArgsConstructor
class InstallationPersonalDataExporter implements PersonalDataExporter {

    private static final String FILE = "installation.json";

    private static final String CHANGES = "changes";

    private final NamedParameterJdbcTemplate jdbc;

    private final InstallationPersonalDataFootprint footprint;

    private final JsonMapper jsonMapper;

    @Override
    public ExportFragment export(ExportScope scope) {

        var table = footprint.table(InstallationPersonalDataFootprint.SETTINGS_CHANGE);
        var person = scope.person();
        var sql = "SELECT " + ExportQueries.columnList(table, scope, InstallationPersonalDataFootprint.ACTOR)
                + " FROM installation_settings_change"
                + person.map(subject -> " WHERE actor_user_id = :subject").orElse("") + " ORDER BY occurred_at, id";
        var rows = jdbc.queryForList(sql, Map.of("subject", person.orElse("")));
        rows.forEach(row -> row.computeIfPresent(CHANGES, (column, json) -> jsonMapper.readTree(json.toString())));
        return new ExportFragment(FILE, Map.of(InstallationPersonalDataFootprint.SETTINGS_CHANGE, rows), List.of());
    }
}
