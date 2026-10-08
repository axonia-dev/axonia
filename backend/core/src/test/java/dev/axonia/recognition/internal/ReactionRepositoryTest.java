package dev.axonia.recognition.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.ReactionPlacementKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@code deletePlacement} binds {@code contributionType} as the {@code ContributionType} enum itself, the same
 * precedent {@code MembershipRepositoryTest} proves for {@code CommunityRole}. Reasoning by analogy is not proof: a
 * binding that silently matched nothing would make {@code ReactionService#unreact} delete no rows and report "No
 * reaction to remove" for a reaction that plainly exists, so this exercises the query against a real Postgres.
 *
 * <p>
 * Each case runs in its own test method, the same discipline {@code ReactionSchemaTest} documents:
 * {@code @DataJdbcTest} wraps a method in one transaction, and a would-be constraint violation would poison the rest of
 * the method.
 */
class ReactionRepositoryTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";

    private static final String USER = "user-1";

    @Autowired
    private ReactionRepository reactions;

    @Autowired
    private ReactionDefinitionRepository definitions;

    @Test
    void deletingAMatchingEmojiPlacementRemovesIt() {

        reactions.save(placement(ContributionType.SLOT_PRESENTED, "🧠", null).build());

        var removed = reactions.deletePlacement(key(ContributionType.SLOT_PRESENTED, "🧠", null));

        assertThat(removed).isEqualTo(1);
    }

    /**
     * The assertion that actually catches a broken enum binding: a binding that matches nothing returns 0 for both the
     * matching and the non-matching case, so only this negative case next to the positive one above proves the bind is
     * selective rather than universally failing.
     */
    @Test
    void deletingWithANonMatchingContributionTypeRemovesNothing() {

        reactions.save(placement(ContributionType.SLOT_PRESENTED, "🧠", null).build());

        var removed = reactions.deletePlacement(key(ContributionType.ARTIFACT_CAPTURED, "🧠", null));

        assertThat(removed).isEqualTo(0);
    }

    /** The custom arm binds {@code definition_id} rather than {@code emoji}; the FK needs a real definition row. */
    @Test
    void deletingAMatchingCustomPlacementRemovesIt() {

        var definition = definitions.save(ReactionDefinitionEntity.builder().organizationId(ORG).name("ship-it")
                .imageKey("k").state(ReactionDefinitionEntity.APPROVED).build());
        reactions.save(placement(ContributionType.SLOT_PRESENTED, null, definition.getId()).build());

        var removed = reactions.deletePlacement(key(ContributionType.SLOT_PRESENTED, null, definition.getId()));

        assertThat(removed).isEqualTo(1);
    }

    /** Two placements on one definition, none on the other: the join query counts per definition, not overall. */
    @Test
    void countUsageByDefinitionCountsPlacementsPerDefinition() {

        var used = definitions.save(ReactionDefinitionEntity.builder().organizationId(ORG).name("high-five")
                .imageKey("k1").state(ReactionDefinitionEntity.APPROVED).build());
        var unused = definitions.save(ReactionDefinitionEntity.builder().organizationId(ORG).name("clapping")
                .imageKey("k2").state(ReactionDefinitionEntity.APPROVED).build());
        reactions.save(placement(ContributionType.SLOT_PRESENTED, null, used.getId()).build());
        reactions.save(
                placement(ContributionType.ARTIFACT_CAPTURED, null, used.getId()).reactorUserId("user-2").build());

        var counts = definitions.countUsageByDefinition(ORG);

        assertThat(counts)
                .extracting(ReactionDefinitionRepository.DefinitionUsage::definitionId,
                        ReactionDefinitionRepository.DefinitionUsage::usageCount)
                .containsExactlyInAnyOrder(tuple(used.getId(), 2L), tuple(unused.getId(), 0L));
    }

    /**
     * The proposer's own queue has to be selective on all three predicates at once. The organization one is the
     * dangerous one: this connection is the table owner and bypasses row-level security by ownership, so nothing but
     * this assertion stands between a finder narrowed to the proposer alone and another tenant's rows in production.
     */
    @Test
    void ownPendingProposalsAreScopedToTheOrganizationTheProposerAndThePendingState() {

        var own = definitions.save(definition("yak-shaving", ORG, ReactionDefinitionEntity.PENDING, USER));
        definitions.save(definition("clapping", ORG, ReactionDefinitionEntity.PENDING, "user-2"));
        definitions.save(definition("high-five", ORG, ReactionDefinitionEntity.APPROVED, USER));
        definitions.save(definition("ship-it", "other-org", ReactionDefinitionEntity.PENDING, USER));

        var mine = definitions.findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(ORG,
                ReactionDefinitionEntity.PENDING, USER);

        assertThat(mine).extracting(ReactionDefinitionEntity::getId).containsExactly(own.getId());
    }

    private static ReactionDefinitionEntity definition(String name, String organizationId, String state,
            String proposedByUserId) {

        return ReactionDefinitionEntity.builder().organizationId(organizationId).name(name).imageKey("k-" + name)
                .state(state).proposedByUserId(proposedByUserId).build();
    }

    private static ReactionEntity.ReactionEntityBuilder placement(ContributionType type, String emoji,
            Long definitionId) {

        return ReactionEntity.builder().organizationId(ORG).communityId(1L).contributionType(type).contributionId(100L)
                .reactorUserId(USER).emoji(emoji).definitionId(definitionId);
    }

    private static ReactionPlacementKey key(ContributionType type, String emoji, Long definitionId) {

        return ReactionPlacementKey.builder().organizationId(ORG).contributionType(type).contributionId(100L)
                .reactorUserId(USER).emoji(emoji).definitionId(definitionId).build();
    }
}
