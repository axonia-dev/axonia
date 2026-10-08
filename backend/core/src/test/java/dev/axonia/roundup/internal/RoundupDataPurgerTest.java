package dev.axonia.roundup.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RoundupDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private RoundupDataPurger purger;

    @Test
    void purgeRemovesTheOrganizationsSetting() {

        jdbc.update("INSERT INTO roundup_settings (organization_id, enabled, created_at, updated_at, version) "
                + "VALUES (:org, true, now(), now(), 0)", Map.of("org", ORG));

        purger.purge(ORG);

        assertThat(countSettings()).isZero();
    }

    private long countSettings() {

        return jdbc.queryForObject("SELECT count(*) FROM roundup_settings WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }
}
