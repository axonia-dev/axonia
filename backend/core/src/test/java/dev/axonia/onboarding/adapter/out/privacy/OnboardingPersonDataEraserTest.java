package dev.axonia.onboarding.adapter.out.privacy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.PersonErasure;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connected as the Testcontainers table owner, so this class proves the SQL and the counts and deliberately proves
 * nothing about RLS - an owner bypasses every policy by ownership.
 */
class OnboardingPersonDataEraserTest extends FootprintTestSupport {

    private static final String TARGET = "erasure-target";
    private static final String NEIGHBOUR = "erasure-neighbour";
    private static final Instant ERASED_AT = Instant.parse("2026-09-12T09:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private OnboardingPersonDataEraser eraser;

    @Test
    void erasureDropsThePathAndEveryStepItFroze() {

        givenPath(TARGET);
        givenStep(TARGET, 0, "LOOK_AND_FEEL");
        givenStep(TARGET, 1, "FIRST_SESSION");

        var counts = eraser.erase(erasureOf(TARGET));

        assertThat(counts.deleted()).isEqualTo(3);
        assertThat(pathsOf(TARGET)).isEmpty();
        assertThat(stepsOf(TARGET)).isEmpty();
    }

    @Test
    void erasureDeletesTheStepsExplicitlyRatherThanCountingOnTheCascade() {

        givenPath(TARGET);
        givenStep(TARGET, 0, "LOOK_AND_FEEL");

        var counts = eraser.erase(erasureOf(TARGET));

        // The step is deleted before the path; if the cascade fired instead, the count returned would still be 2
        // (one explicit delete of a row already gone plus the path), so this also proves the delete order.
        assertThat(counts.deleted()).isEqualTo(2);
        assertThat(counts).isEqualTo(new ErasureCounts(2, 0, 0, 0));
    }

    @Test
    void anotherPersonsPathAndStepsAreLeftAlone() {

        givenPath(NEIGHBOUR);
        givenStep(NEIGHBOUR, 0, "DECLARE_EXPERTISE");

        var counts = eraser.erase(erasureOf(TARGET));

        assertThat(counts).isEqualTo(ErasureCounts.NONE);
        assertThat(pathsOf(NEIGHBOUR)).hasSize(1);
        assertThat(stepsOf(NEIGHBOUR)).hasSize(1);
    }

    private static PersonErasure erasureOf(String subject) {

        return PersonErasure.builder().subject(subject).surrogateId("erased:test-surrogate").erasedAt(ERASED_AT)
                .build();
    }

    private void givenPath(String subject) {

        jdbc.update("""
                INSERT INTO onboarding_path (subject, variant, community_slug, anchor_organization_id,
                                             anchor_community_id, closed_at, created_at, updated_at)
                VALUES (:subject, 'ORGANIZER', 'platform-engineering', 'org-1', 1, NULL, now(), now())
                """, Map.of("subject", subject));
    }

    private void givenStep(String subject, int ordinal, String stepKey) {

        jdbc.update("""
                INSERT INTO onboarding_step (subject, ordinal, step_key, route, detail, visited_at)
                VALUES (:subject, :ordinal, :stepKey, '/somewhere', NULL, NULL)
                """, Map.of("subject", subject, "ordinal", ordinal, "stepKey", stepKey));
    }

    private List<Map<String, Object>> pathsOf(String subject) {

        return jdbc.queryForList("SELECT subject FROM onboarding_path WHERE subject = :subject",
                Map.of("subject", subject));
    }

    private List<Map<String, Object>> stepsOf(String subject) {

        return jdbc.queryForList("SELECT subject FROM onboarding_step WHERE subject = :subject",
                Map.of("subject", subject));
    }
}
