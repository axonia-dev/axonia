package dev.axonia.selfhosted.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.axonia.FootprintLayers;
import dev.axonia.selfhosted.SelfHostedDatabase;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import dev.axonia.shared.api.personaldata.PersonalDataExporter;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layers 1 and 2 of the data-subject-rights gate over the Self-hosted application's schema and the footprints its
 * context holds: the open modules' and this edition's own. The schema is read as the owner, so every table counts.
 */
@SelfHostedSpringBootTest
class SelfHostedPersonalDataFootprintTest {

    @Autowired
    private List<PersonalDataFootprint> footprints;

    @Autowired
    private List<PersonalDataExporter> exporters;

    @Test
    void everyTableOfTheSelfHostedSchemaIsClaimedOnce() {

        // given
        var layers = layers();

        // then
        assertThat(layers.liveTables(Set.of("flyway_schema_history"))).isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(layers.claimedTables());
        assertThat(layers.tablesClaimedTwice()).isEmpty();
    }

    @Test
    void everyColumnOfEveryClaimedTableIsDeclared() {

        layers().assertEveryColumnIsDeclared();
    }

    private FootprintLayers layers() {

        var database = SelfHostedDatabase.shared();
        return new FootprintLayers(new NamedParameterJdbcTemplate(
                new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())),
                footprints, exporters);
    }
}
