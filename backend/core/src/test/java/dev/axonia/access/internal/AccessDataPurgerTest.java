package dev.axonia.access.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class AccessDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AccessDataPurger purger;

    @Test
    void purgeEmptiesEveryRoleForTheOrganizationAndCascadesPermissions() {

        // given — the "default" org may already carry seeded/system roles; count before, assert zero after
        seedRoleGraph();

        // when
        purger.purge(ORG);

        // then — zero is correct even though the org had pre-existing roles: tenant deletion removes ALL of them,
        // including system-seeded ones
        assertThat(count("role")).isZero();
        assertThat(count("role_binding")).isZero();
        assertThat(countRolePermission()).isZero();
    }

    /**
     * Seeds one role with one permission (cascades from role) and one binding for org "default".
     */
    private void seedRoleGraph() {

        var roleId = jdbc.queryForObject("INSERT INTO role (organization_id, name, description, created_at, "
                + "updated_at, version) VALUES (:org, 'purger-test-role', 'seeded for purger test', now(), now(), 0) "
                + "RETURNING id", Map.of("org", ORG), Long.class);

        jdbc.update("INSERT INTO role_permission (role_id, permission) VALUES (:roleId, 'PURGER_TEST_PERMISSION')",
                Map.of("roleId", roleId));

        jdbc.update("INSERT INTO role_binding (organization_id, subject_type, subject_id, role_id, subject_label, "
                + "created_at, updated_at, version) VALUES (:org, 'USER', 'purger-test-user', :roleId, "
                + "'Purger Test User', now(), now(), 0)", Map.of("org", ORG, "roleId", roleId));
    }

    private long count(String table) {

        return switch (table) {
            case "role" -> jdbc.queryForObject("SELECT count(*) FROM role WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            case "role_binding" -> jdbc.queryForObject("SELECT count(*) FROM role_binding WHERE organization_id = :org",
                    Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }

    /**
     * role_permission carries no organization_id column — it is scoped only via its FK to role, which cascades.
     */
    private long countRolePermission() {

        return jdbc.queryForObject("SELECT count(*) FROM role_permission WHERE permission = 'PURGER_TEST_PERMISSION'",
                Map.of(), Long.class);
    }
}
