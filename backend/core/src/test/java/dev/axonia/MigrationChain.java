package dev.axonia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * The folders and placeholders one edition migrates a database with, for the tests that build a database of their own.
 * A folder missing from the classpath is an error here, as it is for the running application. It hands out a loaded
 * {@link Flyway}, never a configuration, so no caller can name other folders.
 */
public final class MigrationChain {

    public static final MigrationChain SELF_HOSTED = new MigrationChain(
            List.of("classpath:db/selfhosted-baseline", "classpath:db/migration"),
            Map.of("app-password", "axonia_app", "communication-read-password", "communication_read"));

    private final List<String> locations;

    private final Map<String, String> placeholders;

    public MigrationChain(List<String> locations, Map<String, String> placeholders) {

        this.locations = List.copyOf(locations);
        this.placeholders = Map.copyOf(placeholders);
    }

    /** The same folders, with some placeholders given other values, as an operator configures them. */
    public MigrationChain withPlaceholders(Map<String, String> values) {

        var merged = new HashMap<>(placeholders);
        merged.putAll(values);
        return new MigrationChain(locations, merged);
    }

    /** The {@code spring.flyway.locations} value that names this chain. */
    public String locationsProperty() {

        return String.join(",", locations);
    }

    public Flyway on(DedicatedPostgresContainer pg) {

        return configured(pg).load();
    }

    /** This chain on {@code pg}, stopping at {@code version}. */
    public Flyway upTo(DedicatedPostgresContainer pg, String version) {

        return configured(pg).target(version).load();
    }

    private FluentConfiguration configured(DedicatedPostgresContainer pg) {

        return Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(locations.toArray(String[]::new)).placeholders(placeholders).failOnMissingLocations(true);
    }
}
