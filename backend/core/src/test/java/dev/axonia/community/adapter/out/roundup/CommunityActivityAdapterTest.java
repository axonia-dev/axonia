package dev.axonia.community.adapter.out.roundup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.adapter.out.persistence.CommunityEntityMother;
import dev.axonia.community.adapter.out.persistence.CommunityKindEntityMother;
import dev.axonia.community.adapter.out.persistence.CommunityKindRepository;
import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.roundup.api.spi.CommunityActivity.CommunitySummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CommunityActivityAdapterTest extends CoreJdbcTestSupport {

    /**
     * Its own organization, never the shared "default": this port answers for a whole organization at once, so a row
     * another test committed there would be part of the answer.
     */
    private static final String ORG = "roundup-communities";

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    private CommunityActivityAdapter sut;

    private Long kindId;

    @BeforeEach
    void setUp() {

        kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
        sut = new CommunityActivityAdapter(communityRepository);
    }

    @Test
    void activeCommunities_namesEveryLiveCommunityOfTheOrganizationByName() {

        // given
        var data = community("data-chapter", "Data Chapter", ORG, CommunityStatus.ACTIVE, null, null);
        var backend = community("backend-guild", "Backend Guild", ORG, CommunityStatus.ACTIVE, null, null);

        // when
        var actual = sut.activeCommunities(ORG);

        // then: ordered by name, so a roundup lists them the same way twice running
        assertThat(actual).extracting(CommunitySummary::id, CommunitySummary::name)
                .containsExactly(tuple(backend, "Backend Guild"), tuple(data, "Data Chapter"));
    }

    @Test
    void activeCommunities_excludesArchivedCommunities() {

        // given
        community("closed-guild", "Closed Guild", ORG, CommunityStatus.ARCHIVED, null, null);

        // when
        var actual = sut.activeCommunities(ORG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void activeCommunities_readsOnlyTheGivenOrganization() {

        // given
        community("their-guild", "Their Guild", "acme", CommunityStatus.ACTIVE, null, null);

        // when
        var actual = sut.activeCommunities(ORG);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void aCommunitySummaryCarriesTheLookAndFeelTheRoundupRendersItWith() {

        // given
        var id = community("platform-guild", "Platform Guild", ORG, CommunityStatus.ACTIVE, "badges/pg.png", "#6e3ceb");

        // when
        var actual = sut.activeCommunities(ORG);

        // then
        assertThat(actual).singleElement()
                .isEqualTo(new CommunitySummary(id, "Platform Guild", "badges/pg.png", "#6e3ceb"));
    }

    @Test
    void aCommunityThatNeverChoseALookIsSummarisedWithNeitherHalfOfOne() {

        // given
        var id = community("voice-test-guild", "Voice Test Guild", ORG, CommunityStatus.ACTIVE, null, null);

        // when
        var actual = sut.activeCommunities(ORG);

        // then
        assertThat(actual).singleElement().isEqualTo(new CommunitySummary(id, "Voice Test Guild", null, null));
    }

    private long community(String slug, String name, String organizationId, CommunityStatus status, String badgeKey,
            String accentColor) {

        return communityRepository.save(CommunityEntityMother.any().kindId(kindId).slug(slug).name(name)
                .organizationId(organizationId).status(status).badgeKey(badgeKey).accentColor(accentColor).build())
                .getId();
    }
}
