package dev.axonia.expertise.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertiseDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private ExpertiseDataPurger purger;

    @Test
    void purgeEmptiesEveryExpertiseTableForTheOrganization() {

        // given - one row in each of the nine expertise tables, all for org "default"
        seedExpertiseTables();

        // when
        purger.purge(ORG);

        // then
        assertThat(count("expertise_signal")).isZero();
        assertThat(count("expertise_tag_alias")).isZero();
        assertThat(count("expertise_opt_out")).isZero();
        assertThat(count("expertise_hidden_tag")).isZero();
        assertThat(count("expertise_dismissed_signal")).isZero();
        assertThat(count("expertise_tag_dismissal")).isZero();
        assertThat(count("expertise_declaration")).isZero();
        assertThat(count("expertise_named_area")).isZero();
        assertThat(count("expertise_named_area_tag")).isZero();
    }

    @Test
    void purgeLeavesAnotherOrganizationsDeclarationsUntouched() {

        // given
        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, 'purger-test-user', 'java', now())", Map.of("org", OTHER_ORG));

        // when
        purger.purge(ORG);

        // then
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expertise_declaration WHERE organization_id = :org",
                Map.of("org", OTHER_ORG), Long.class)).isEqualTo(1);
    }

    /**
     * Seeds one row in each of the nine expertise tables for org "default".
     */
    private void seedExpertiseTables() {

        jdbc.update("INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                + "source_type, source_id, occurred_at) VALUES (:org, 1, 'purger-test-user', 'java', 'CONTRIBUTED', "
                + "'ARTIFACT', 1, now())", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_tag_alias (organization_id, community_id, variant_tag, canonical_tag) "
                + "VALUES (:org, 1, 'js', 'javascript')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, 'purger-test-user')",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) "
                + "VALUES (:org, 'purger-test-user', 'java')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                + "VALUES (:org, 'purger-test-user', 'ARTIFACT', 1, 'java')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_tag_dismissal (organization_id, community_id, tag_low, tag_high) "
                + "VALUES (:org, 1, 'spring', 'spring-boot')", Map.of("org", ORG));
        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, 'purger-test-user', 'java', now())", Map.of("org", ORG));
        var namedArea = jdbc.queryForObject("INSERT INTO expertise_named_area (organization_id, community_id, name) "
                + "VALUES (:org, 1, 'Streaming') RETURNING id", Map.of("org", ORG), Long.class);
        jdbc.update(
                "INSERT INTO expertise_named_area_tag (organization_id, community_id, named_area_id, canonical_tag) "
                        + "VALUES (:org, 1, :namedArea, 'kafka')",
                Map.of("org", ORG, "namedArea", namedArea));
    }

    private long count(String table) {

        return switch (table) {
            case "expertise_signal" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_signal WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_tag_alias" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_tag_alias WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_opt_out" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_opt_out WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_hidden_tag" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_hidden_tag WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_dismissed_signal" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_dismissed_signal WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_tag_dismissal" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_tag_dismissal WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_declaration" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_declaration WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_named_area" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_named_area WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "expertise_named_area_tag" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_named_area_tag WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }
}
