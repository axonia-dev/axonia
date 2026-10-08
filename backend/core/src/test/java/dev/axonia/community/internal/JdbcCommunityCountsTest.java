package dev.axonia.community.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.CommunityStatus;
import dev.axonia.community.mother.CommunityEntityMother;
import dev.axonia.community.mother.CommunityKindEntityMother;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That archived communities do not count is what makes the count worth reading
 * ({@link dev.axonia.community.CommunityCounts}'s own javadoc), and before this test it existed only there: no test
 * seeded a community row at all, so rewriting the adapter's predicate to anything - or deleting it - would have left
 * every other test green.
 */
class JdbcCommunityCountsTest extends CoreJdbcTestSupport {

    private static final String ORG = "counts-active-communities";

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private JdbcCommunityCounts sut;

    private Long kindId;

    @BeforeEach
    void setUp() {

        kindId = communityKindRepository.save(CommunityKindEntityMother.any().organizationId(ORG).build()).getId();
        sut = new JdbcCommunityCounts(jdbc);
    }

    @Test
    void countsOnlyActiveCommunitiesExcludingArchivedOnes() {

        community("guild-one", CommunityStatus.ACTIVE);
        community("guild-two", CommunityStatus.ACTIVE);
        community("guild-retired", CommunityStatus.ARCHIVED);

        // Non-zero, and different from the 3 communities actually seeded: the archived one must not count.
        assertThat(sut.countActive(ORG)).isEqualTo(2).isNotEqualTo(3);
    }

    @Test
    void countsNothingForAnOrganizationWithNoCommunities() {

        assertThat(sut.countActive("counts-active-communities-none")).isZero();
    }

    @Test
    void neverCountsAnotherOrganizationsCommunities() {

        community("their-guild", CommunityStatus.ACTIVE);

        assertThat(sut.countActive("counts-active-communities-someone-else")).isZero();
    }

    private void community(String slug, CommunityStatus status) {

        communityRepository
                .save(CommunityEntityMother.any().kindId(kindId).slug(slug).organizationId(ORG).status(status).build());
    }
}
