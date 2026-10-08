package dev.axonia.access.internal;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class AccessMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AccessMemberDataEraser eraser;

    /**
     * A GROUP binding's subject_id is a group name, not a user id, but nothing stops it from colliding textually with
     * one. Seeding a GROUP binding whose subject_id equals the target's user id proves the subject_type predicate is
     * load bearing: without it, this binding would be swept up too, even though it names a group, not the erased
     * member.
     */
    @Test
    void deletesOnlyTheUserBindingAndSparesNeighbourAndGroupBindings() {

        var roleId = seedRole();
        seedBinding("USER", TARGET, roleId);
        seedBinding("USER", NEIGHBOUR, roleId);
        seedBinding("GROUP", TARGET, roleId); // a group named the same as the target's user id

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(countBindings("USER", TARGET)).isZero();
        assertThat(countBindings("USER", NEIGHBOUR)).isEqualTo(1);
        assertThat(countBindings("GROUP", TARGET)).isEqualTo(1);
    }

    private Long seedRole() {

        return jdbc.queryForObject("INSERT INTO role (organization_id, name, description, created_at, updated_at, "
                + "version) VALUES (:org, 'eraser-test-role', 'seeded for eraser test', now(), now(), 0) "
                + "RETURNING id", Map.of("org", ORG), Long.class);
    }

    private void seedBinding(String subjectType, String subjectId, Long roleId) {

        jdbc.update(
                "INSERT INTO role_binding (organization_id, subject_type, subject_id, role_id, subject_label, "
                        + "created_at, updated_at, version) VALUES (:org, :subjectType, :subjectId, :roleId, "
                        + "'Eraser Test Subject', now(), now(), 0)",
                Map.of("org", ORG, "subjectType", subjectType, "subjectId", subjectId, "roleId", roleId));
    }

    private long countBindings(String subjectType, String subjectId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM role_binding WHERE organization_id = :org "
                        + "AND subject_type = :subjectType AND subject_id = :subjectId",
                Map.of("org", ORG, "subjectType", subjectType, "subjectId", subjectId), Long.class);
    }
}
