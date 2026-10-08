package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertiseMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String OTHER_ORG = "other-org";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private ExpertiseMemberDataEraser eraser;

    @Test
    void deletesTheTargetsRowsAcrossAllFiveTablesAndSparesTheNeighbour() {

        seedExpertiseGraph(TARGET);
        seedExpertiseGraph(NEIGHBOUR);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.deleted()).isEqualTo(5);
        assertThat(count("expertise_signal", TARGET)).isZero();
        assertThat(count("expertise_hidden_tag", TARGET)).isZero();
        assertThat(count("expertise_opt_out", TARGET)).isZero();
        assertThat(count("expertise_dismissed_signal", TARGET)).isZero();
        assertThat(count("expertise_declaration", TARGET)).isZero();
        assertThat(count("expertise_signal", NEIGHBOUR)).isEqualTo(1);
        assertThat(count("expertise_hidden_tag", NEIGHBOUR)).isEqualTo(1);
        assertThat(count("expertise_opt_out", NEIGHBOUR)).isEqualTo(1);
        assertThat(count("expertise_dismissed_signal", NEIGHBOUR)).isEqualTo(1);
        assertThat(count("expertise_declaration", NEIGHBOUR)).isEqualTo(1);
    }

    @Test
    void leavesTheSameMembersDeclarationsInAnotherOrganizationUntouched() {

        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, :user, 'java', now())", Map.of("org", OTHER_ORG, "user", TARGET));

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId("erased:test-surrogate")
                .erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM expertise_declaration WHERE organization_id = :org AND user_id = :user",
                Map.of("org", OTHER_ORG, "user", TARGET), Long.class)).isEqualTo(1);
    }

    private void seedExpertiseGraph(String userId) {

        jdbc.update("INSERT INTO expertise_signal (organization_id, community_id, user_id, tag, signal_type, "
                + "source_type, source_id, occurred_at) VALUES (:org, 1, :user, 'java', 'CONTRIBUTED', 'ARTIFACT', 1, "
                + "now())", Map.of("org", ORG, "user", userId));
        jdbc.update("INSERT INTO expertise_hidden_tag (organization_id, user_id, tag) VALUES (:org, :user, 'java')",
                Map.of("org", ORG, "user", userId));
        jdbc.update("INSERT INTO expertise_opt_out (organization_id, user_id) VALUES (:org, :user)",
                Map.of("org", ORG, "user", userId));
        jdbc.update("INSERT INTO expertise_dismissed_signal (organization_id, user_id, source_type, source_id, tag) "
                + "VALUES (:org, :user, 'ARTIFACT', 1, 'java')", Map.of("org", ORG, "user", userId));
        jdbc.update("INSERT INTO expertise_declaration (organization_id, community_id, user_id, tag, declared_at) "
                + "VALUES (:org, 1, :user, 'java', now())", Map.of("org", ORG, "user", userId));
    }

    private long count(String table, String userId) {

        return switch (table) {
            case "expertise_signal" -> jdbc.queryForObject(
                    "SELECT count(*) FROM expertise_signal WHERE organization_id = :org " + "AND user_id = :user",
                    Map.of("org", ORG, "user", userId), Long.class);
            case "expertise_hidden_tag" -> jdbc.queryForObject(
                    "SELECT count(*) FROM expertise_hidden_tag WHERE organization_id = :org " + "AND user_id = :user",
                    Map.of("org", ORG, "user", userId), Long.class);
            case "expertise_opt_out" -> jdbc.queryForObject(
                    "SELECT count(*) FROM expertise_opt_out WHERE organization_id = :org " + "AND user_id = :user",
                    Map.of("org", ORG, "user", userId), Long.class);
            case "expertise_dismissed_signal" ->
                jdbc.queryForObject("SELECT count(*) FROM expertise_dismissed_signal WHERE organization_id = :org "
                        + "AND user_id = :user", Map.of("org", ORG, "user", userId), Long.class);
            case "expertise_declaration" -> jdbc.queryForObject(
                    "SELECT count(*) FROM expertise_declaration WHERE organization_id = :org AND user_id = :user",
                    Map.of("org", ORG, "user", userId), Long.class);
            default -> throw new IllegalArgumentException("Unexpected expertise table: " + table);
        };
    }
}
