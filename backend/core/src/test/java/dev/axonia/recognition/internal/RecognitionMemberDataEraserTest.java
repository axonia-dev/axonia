package dev.axonia.recognition.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class RecognitionMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other-org";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";
    private static final String SURROGATE = "erased:test-surrogate";
    private static final Instant ERASED_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private RecognitionMemberDataEraser eraser;

    @Test
    void anonymisesLoveWithoutChangingTheCountAndSparesTheNeighbour() {

        var contribution = seedContribution(ORG);
        seedLove(ORG, TARGET, contribution);
        seedLove(ORG, NEIGHBOUR, contribution);
        // same user id, but a different organization - erasing the target in ORG must not touch it
        var otherOrgContribution = seedContribution(OTHER_ORG);
        seedLove(OTHER_ORG, TARGET, otherOrgContribution);

        var before = jdbc.queryForObject("SELECT count(*) FROM contribution_love WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.anonymised()).isEqualTo(1);
        // the love total is the point: a row that vanishes is a number that silently changes for everyone else
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contribution_love WHERE organization_id = :org",
                Map.of("org", ORG), Long.class)).isEqualTo(before);
        assertThat(loveBy(ORG, SURROGATE)).isEqualTo(1);
        assertThat(loveBy(ORG, TARGET)).isZero();
        assertThat(loveBy(ORG, NEIGHBOUR)).isEqualTo(1);
        // the other-organization love is untouched: same user id, wrong tenant
        assertThat(loveBy(OTHER_ORG, TARGET)).isEqualTo(1);
    }

    /**
     * An anonymised row must also come out of the erasure with a fresh {@code version} and an {@code updated_at} taken
     * from the erasure's own instant. Leaving the version alone lets a request that loaded the row first still satisfy
     * its own optimistic-lock predicate and write the pre-erasure subject straight back.
     */
    @Test
    void anAnonymisedLoveIsVersionBumpedAndStampedWithTheErasuresOwnInstant() {

        var contribution = seedContribution(ORG);
        seedLove(ORG, TARGET, contribution);

        eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        var row = jdbc.queryForMap(
                "SELECT version, updated_at FROM contribution_love "
                        + "WHERE organization_id = :org AND lover_user_id = :surrogate",
                Map.of("org", ORG, "surrogate", SURROGATE));
        assertThat(row).containsEntry("version", 1L).containsEntry("updated_at", Timestamp.from(ERASED_AT));
    }

    /**
     * A reaction is a thing the member said, so it is deleted outright, not anonymised - unlike a love, where the row
     * survives and only the identity is replaced.
     */
    @Test
    void erasingDeletesTheMembersOwnPlacementsButSparesOtherReactorsAndOrganizations() {

        var definitionId = seedDefinition(ORG, "ship-it", NEIGHBOUR, NEIGHBOUR);
        seedPlacement(ORG, TARGET, "🧠", null);
        seedPlacement(ORG, TARGET, null, definitionId);
        seedPlacement(ORG, NEIGHBOUR, "🧠", null);
        seedPlacement(OTHER_ORG, TARGET, "🧠", null);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.deleted()).isEqualTo(2);
        assertThat(placementsBy(ORG, TARGET)).isZero();
        assertThat(placementsBy(ORG, NEIGHBOUR)).isEqualTo(1);
        assertThat(placementsBy(OTHER_ORG, TARGET)).isEqualTo(1);
    }

    /**
     * A proposal is cleared in place, never deleted: the word itself, and the approval somebody else gave it, survive
     * the proposer's erasure. {@code version} still bumps and {@code updated_at} still stamps from the erasure's own
     * instant, for the same resurrection reason {@code contribution_love}'s anonymise does.
     */
    @Test
    void erasingClearsOnlyTheErasedMembersOwnIdentityOnAReactionDefinition() {

        var proposedByTarget = seedDefinition(ORG, "target-proposed", TARGET, NEIGHBOUR);
        var approvedByTarget = seedDefinition(ORG, "target-approved", NEIGHBOUR, TARGET);
        var untouched = seedDefinition(ORG, "untouched", NEIGHBOUR, NEIGHBOUR);
        var otherOrgProposedByTarget = seedDefinition(OTHER_ORG, "target-proposed", TARGET, TARGET);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET).surrogateId(SURROGATE)
                .erasedAt(ERASED_AT).build());

        assertThat(counts.cleared()).isEqualTo(2);
        assertThat(definitionRow(proposedByTarget)).containsEntry("proposed_by_user_id", null)
                .containsEntry("approved_by_user_id", NEIGHBOUR).containsEntry("version", 1L)
                .containsEntry("updated_at", Timestamp.from(ERASED_AT));
        assertThat(definitionRow(approvedByTarget)).containsEntry("proposed_by_user_id", NEIGHBOUR)
                .containsEntry("approved_by_user_id", null);
        assertThat(definitionRow(untouched)).containsEntry("proposed_by_user_id", NEIGHBOUR)
                .containsEntry("approved_by_user_id", NEIGHBOUR).containsEntry("version", 0L);
        // same user id, but a different organization - erasing the target in ORG must not touch it
        assertThat(definitionRow(otherOrgProposedByTarget)).containsEntry("proposed_by_user_id", TARGET)
                .containsEntry("approved_by_user_id", TARGET);
    }

    private void seedPlacement(String org, String reactor, String emoji, Long definitionId) {

        var params = new MapSqlParameterSource().addValue("org", org).addValue("reactor", reactor)
                .addValue("emoji", emoji).addValue("definitionId", definitionId);
        jdbc.update(
                "INSERT INTO contribution_reaction (organization_id, community_id, contribution_type, "
                        + "contribution_id, reactor_user_id, emoji, definition_id, created_at, updated_at, version) "
                        + "VALUES (:org, 1, 'ARTIFACT_CAPTURED', 1, :reactor, :emoji, :definitionId, now(), now(), 0)",
                params);
    }

    private long placementsBy(String org, String reactor) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM contribution_reaction WHERE organization_id = :org AND reactor_user_id = :user",
                Map.of("org", org, "user", reactor), Long.class);
    }

    private Long seedDefinition(String org, String name, String proposedBy, String approvedBy) {

        return jdbc.queryForObject(
                "INSERT INTO reaction_definition (organization_id, name, image_key, state, "
                        + "proposed_by_user_id, approved_by_user_id, created_at, updated_at, version) VALUES "
                        + "(:org, :name, 'k', 'APPROVED', :proposedBy, :approvedBy, now(), now(), 0) RETURNING id",
                Map.of("org", org, "name", name, "proposedBy", proposedBy, "approvedBy", approvedBy), Long.class);
    }

    private Map<String, Object> definitionRow(Long id) {

        return jdbc.queryForMap(
                "SELECT proposed_by_user_id, approved_by_user_id, version, updated_at FROM reaction_definition "
                        + "WHERE id = :id",
                Map.of("id", id));
    }

    /**
     * Seeds one recognized_contribution. Since V4 the row names no contributor - credit is derived from the owning
     * bounded context at read time - so seeding it needs no user id.
     */
    private Long seedContribution(String org) {

        return jdbc.queryForObject(
                "INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, "
                        + "community_id, title, occurred_at, created_at, updated_at, version) "
                        + "VALUES (:org, 'ARTIFACT', 1, 1, 'Eraser Test Contribution', now(), now(), now(), 0) "
                        + "RETURNING id",
                Map.of("org", org), Long.class);
    }

    private void seedLove(String org, String lover, Long contributionId) {

        jdbc.update(
                "INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id, "
                        + "created_at, updated_at, version) VALUES (:org, :contributionId, :lover, now(), now(), 0)",
                Map.of("org", org, "contributionId", contributionId, "lover", lover));
    }

    private long loveBy(String org, String lover) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM contribution_love WHERE organization_id = :org " + "AND lover_user_id = :lover",
                Map.of("org", org, "lover", lover), Long.class);
    }
}
