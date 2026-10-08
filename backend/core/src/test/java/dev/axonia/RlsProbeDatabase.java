package dev.axonia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

/**
 * A throwaway Postgres migrated by the edition chain it is given, for the raw JDBC isolation probes. The baseline seeds
 * no Organization, so this seeds the one the probes read, {@code rls-probe}, as the table owner: ownership bypasses
 * RLS, so the row genuinely exists and an invisibility assertion over it is meaningful rather than vacuously true.
 */
public final class RlsProbeDatabase {

    private final DedicatedPostgresContainer pg = new DedicatedPostgresContainer();

    private final MigrationChain chain;

    public RlsProbeDatabase(MigrationChain chain) {

        this.chain = chain;
    }

    public void start() {

        pg.start();
        chain.on(pg).migrate();
        try (var c = asOwner(); var s = c.createStatement()) {
            s.execute("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES ('rls-probe','RLS Probe','ENTERPRISE', now(), now())");
        } catch (SQLException e) {
            throw new IllegalStateException("could not seed the probe organization", e);
        }
    }

    public void stop() {

        pg.stop();
    }

    /** A connection as the schema owner, which bypasses RLS. */
    public Connection asOwner() throws SQLException {

        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    /** A connection as the least-privilege runtime role, which RLS applies to. */
    public Connection asApp() throws SQLException {

        return DriverManager.getConnection(pg.getJdbcUrl(), "axonia_app", "axonia_app");
    }

    /** Every table in {@code public} with row-level security enabled. */
    public Set<String> rlsEnabledTables() throws SQLException {

        var tables = new TreeSet<String>();
        try (var c = asOwner();
                var s = c.createStatement();
                var rs = s.executeQuery("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }
}
