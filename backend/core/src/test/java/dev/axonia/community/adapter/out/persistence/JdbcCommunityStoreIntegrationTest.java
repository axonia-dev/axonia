package dev.axonia.community.adapter.out.persistence;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;
import dev.axonia.community.application.port.out.CommunitySearch;
import dev.axonia.community.domain.CommunityRecord;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCommunityStoreIntegrationTest extends CoreJdbcTestSupport {

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    private JdbcCommunityStore sut;

    private Long joinedGuild;
    private Long joinedCircle;
    private Long openGuild;
    private Long otherOpenGuild;

    @BeforeEach
    void seed() {

        sut = new JdbcCommunityStore(communityRepository);
        var kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
        joinedGuild = save(kindId, "default", "joined-guild", "Joined Guild", CommunityStatus.ACTIVE);
        joinedCircle = save(kindId, "default", "joined-circle", "Joined Circle", CommunityStatus.ACTIVE);
        openGuild = save(kindId, "default", "open-guild", "Open Guild", CommunityStatus.ACTIVE);
        otherOpenGuild = save(kindId, "default", "second-guild", "Second Guild", CommunityStatus.ACTIVE);
        save(kindId, "default", "archived-guild", "Archived Guild", CommunityStatus.ARCHIVED);
        save(kindId, "elsewhere", "foreign-guild", "Foreign Guild", CommunityStatus.ACTIVE);
    }

    @Test
    void joined_combines_with_name_organization_and_status() {

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, Set.of(joinedGuild, joinedCircle)),
                PageRequest.of(0, 10));

        // then
        assertThat(actual.getContent()).extracting(CommunityRecord::getId).containsExactly(joinedGuild);
        assertThat(actual.getTotalElements()).isEqualTo(1);
    }

    @Test
    void explore_excludes_the_memberships_and_counts_beyond_the_page() {

        // when
        var actual = sut.search(search(MembershipFilter.EXPLORE, Set.of(joinedGuild, joinedCircle)),
                PageRequest.of(0, 1));

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getContent().getFirst().getId()).isIn(openGuild, otherOpenGuild);
        assertThat(actual.getTotalElements()).isEqualTo(2);
        assertThat(actual.getTotalPages()).isEqualTo(2);
    }

    @Test
    void unfiltered_reads_every_active_match_of_the_organization() {

        // when
        var actual = sut.search(search(null, Set.of(joinedGuild)), PageRequest.of(0, 1));

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getTotalElements()).isEqualTo(3);
    }

    private Long save(Long kindId, String organizationId, String slug, String name, CommunityStatus status) {

        return communityRepository.save(CommunityEntityMother.any().organizationId(organizationId).kindId(kindId)
                .slug(slug).name(name).status(status).build()).getId();
    }

    private static CommunitySearch search(MembershipFilter membership, Set<Long> memberOf) {

        return CommunitySearch.builder().organizationId("default").status(CommunityStatus.ACTIVE).name("guild")
                .membership(membership).memberOf(memberOf).build();
    }
}
