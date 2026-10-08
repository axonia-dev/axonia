package dev.axonia.selfhosted.internal;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

import dev.axonia.selfhosted.SelfHostedDatabase;

/**
 * A database of its own in the Self-hosted tests' Postgres, for a test whose first start must find it empty. The
 * baseline's roles already exist in that Postgres and its role blocks skip them, so the application migrates and
 * connects to it exactly as to the shared one.
 */
final class FreshInstallation {

    private static final AtomicInteger NEXT = new AtomicInteger();

    private static final List<String> FOUNDED = List.of(
            "SELECT organization_id, subject_claim, founded_at FROM selfhosted_settings",
            "SELECT id, name, kind, lifecycle_state, updated_at FROM organization",
            "SELECT id, name, slug FROM role ORDER BY id",
            "SELECT id, subject_type, subject_id, subject_label, role_id FROM role_binding ORDER BY id");

    private final String url;

    private FreshInstallation(String url) {

        this.url = url;
    }

    static FreshInstallation create() throws SQLException {

        var name = "installation_" + NEXT.incrementAndGet();
        SelfHostedDatabase.asOwner("CREATE DATABASE " + name);
        var database = SelfHostedDatabase.shared();
        return new FreshInstallation(
                "jdbc:postgresql://" + database.getHost() + ":" + database.getFirstMappedPort() + "/" + name);
    }

    /** The settings that point an application at this database, with {@code overrides} applied over them. */
    Map<String, String> settings(Map<String, String> overrides) {

        var settings = new HashMap<String, String>();
        settings.put("spring.flyway.url", url);
        settings.put("spring.datasource.url", url);
        settings.put("axonia.tenant-discovery.datasource.url", url);
        settings.putAll(overrides);
        return settings;
    }

    /** Every row {@code sql} returns, read as the owner, each as its columns joined by {@code |}. */
    List<String> rows(String sql) throws SQLException {

        var database = SelfHostedDatabase.shared();
        try (var connection = DriverManager.getConnection(url, database.getUsername(), database.getPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            var rows = new ArrayList<String>();
            var columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                var row = new StringJoiner("|");
                for (var column = 1; column <= columns; column++) {
                    row.add(result.getString(column));
                }
                rows.add(row.toString());
            }
            return rows;
        }
    }

    /** What a founding writes: the record, the registry row, the roles and every binding. */
    List<String> snapshot() throws SQLException {

        var rows = new ArrayList<String>();
        for (var query : FOUNDED) {
            rows.addAll(rows(query));
        }
        return rows;
    }
}
