package dev.axonia.recognition.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

import dev.axonia.PurgerTestSupport;
import dev.axonia.media.api.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RecognitionDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private RecognitionDataPurger purger;

    @Autowired
    private MediaStorage mediaStorage;

    @BeforeEach
    void resetMediaStorageStub() {
        reset(mediaStorage);
    }

    @Test
    void purgeEmptiesEveryRecognitionTableForTheOrganization() {

        // given — a recognized contribution with one love riding it
        seedRecognitionGraph();

        // when
        purger.purge(ORG);

        // then — love cascades from recognized_contribution, but is asserted directly too (defensive delete)
        assertThat(count("recognized_contribution")).isZero();
        assertThat(count("contribution_love")).isZero();
    }

    /**
     * The FK from {@code contribution_reaction} to {@code reaction_definition} is {@code ON DELETE CASCADE}, but the
     * purger deletes both explicitly, ahead of the cascade - the same defensive choice
     * {@code recognized_contribution}/{@code contribution_love} already makes above.
     */
    @Test
    void purgeDeletesReactionPlacementsAndDefinitions() {

        // given - one custom reaction (its placement rides the definition) and one plain emoji placement
        var definitionId = seedDefinition("ship-it", "reactions/default/ship-it.png");
        seedPlacement(definitionId, null);
        seedPlacement(null, "🧠");

        // when
        purger.purge(ORG);

        // then
        assertThat(count("contribution_reaction")).isZero();
        assertThat(count("reaction_definition")).isZero();
    }

    /**
     * Object storage has no rollback, so the blob delete must not happen until the purge's own transaction has actually
     * committed - otherwise a later purger in the same hub-deletion transaction failing would leave the row back
     * (rolled back) but the blob already gone. Right after {@code purge()} returns, still inside the still-open
     * transaction, nothing has reached {@link MediaStorage} yet; only once the transaction is forced to commit does the
     * deferred delete fire.
     */
    @Test
    void purgeDeletesTheDefinitionsImageBlobOnlyAfterTheTransactionCommits() {

        // given
        seedDefinition("ship-it", "reactions/default/ship-it.png");

        // when - the purge runs, but the transaction it runs in has not committed yet
        purger.purge(ORG);
        verifyNoInteractions(mediaStorage);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        // then - only now, after the commit, does the blob go
        verify(mediaStorage).delete("reactions/default/ship-it.png");
    }

    /**
     * Seeds one recognized_contribution and one contribution_love riding it, both for org "default".
     */
    private void seedRecognitionGraph() {

        var contributionId = jdbc.queryForObject(
                "INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, "
                        + "community_id, title, occurred_at, created_at, updated_at, version) "
                        + "VALUES (:org, 'ARTIFACT', 1, 1, 'Purger Test Contribution', now(), "
                        + "now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);

        jdbc.update("INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id, "
                + "created_at, updated_at, version) VALUES (:org, :contributionId, 'lover-user', now(), " + "now(), 0)",
                Map.of("org", ORG, "contributionId", contributionId));
    }

    private Long seedDefinition(String name, String imageKey) {

        return jdbc.queryForObject("INSERT INTO reaction_definition (organization_id, name, image_key, state, "
                + "proposed_by_user_id, approved_by_user_id, created_at, updated_at, version) VALUES "
                + "(:org, :name, :imageKey, 'APPROVED', 'proposer-user', 'approver-user', now(), now(), 0) "
                + "RETURNING id", Map.of("org", ORG, "name", name, "imageKey", imageKey), Long.class);
    }

    private void seedPlacement(Long definitionId, String emoji) {

        var params = new MapSqlParameterSource().addValue("org", ORG).addValue("definitionId", definitionId)
                .addValue("emoji", emoji);
        jdbc.update("INSERT INTO contribution_reaction (organization_id, community_id, contribution_type, "
                + "contribution_id, reactor_user_id, emoji, definition_id, created_at, updated_at, version) "
                + "VALUES (:org, 1, 'ARTIFACT_CAPTURED', 1, 'reactor-user', :emoji, :definitionId, now(), now(), 0)",
                params);
    }

    private long count(String table) {

        return switch (table) {
            case "recognized_contribution" ->
                jdbc.queryForObject("SELECT count(*) FROM recognized_contribution WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "contribution_love" ->
                jdbc.queryForObject("SELECT count(*) FROM contribution_love WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "contribution_reaction" ->
                jdbc.queryForObject("SELECT count(*) FROM contribution_reaction WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            case "reaction_definition" ->
                jdbc.queryForObject("SELECT count(*) FROM reaction_definition WHERE organization_id = :org",
                        Map.of("org", ORG), Long.class);
            default -> throw new IllegalArgumentException("Unexpected org-scoped table: " + table);
        };
    }
}
