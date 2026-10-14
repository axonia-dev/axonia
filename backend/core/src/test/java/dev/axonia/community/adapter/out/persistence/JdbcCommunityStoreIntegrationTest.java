package dev.axonia.community.adapter.out.persistence;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

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

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private JdbcCommunityStore sut;

    private Long joinedGuild;
    private Long joinedCircle;
    private Long joinedSecondGuild;
    private Long percentClub;
    private Long underscoreClub;
    private Long plainClub;
    private Long openGuild;
    private Long otherOpenGuild;

    @BeforeEach
    void seed() {

        sut = new JdbcCommunityStore(communityRepository, jdbcAggregateTemplate);
        var kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
        joinedGuild = save(kindId, "default", "joined-guild", "Joined Guild", CommunityStatus.ACTIVE);
        joinedCircle = save(kindId, "default", "joined-circle", "Joined Circle", CommunityStatus.ACTIVE);
        joinedSecondGuild = save(kindId, "default", "joined-second", "Joined Second Guild", CommunityStatus.ACTIVE);
        percentClub = save(kindId, "default", "percent-club", "100% Club", CommunityStatus.ACTIVE);
        underscoreClub = save(kindId, "default", "underscore-club", "A_B Club", CommunityStatus.ACTIVE);
        plainClub = save(kindId, "default", "plain-club", "AxB Club", CommunityStatus.ACTIVE);
        openGuild = save(kindId, "default", "open-guild", "Open Guild", CommunityStatus.ACTIVE);
        otherOpenGuild = save(kindId, "default", "second-guild", "Second Guild", CommunityStatus.ACTIVE);
        save(kindId, "default", "archived-guild", "Archived Guild", CommunityStatus.ARCHIVED);
        save(kindId, "elsewhere", "foreign-guild", "Foreign Guild", CommunityStatus.ACTIVE);
    }

    @Test
    void joined_combines_with_name_organization_and_status_and_counts_beyond_the_page() {

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, Set.of(joinedGuild, joinedCircle, joinedSecondGuild)),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "created_at")));

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getContent().getFirst().getId()).isIn(joinedGuild, joinedSecondGuild);
        assertThat(actual.getTotalElements()).isEqualTo(2);
    }

    @Test
    void the_search_text_matches_percent_literally() {

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, "%", Set.of(percentClub, plainClub, joinedGuild)),
                PageRequest.of(0, 10));

        // then
        assertThat(actual.getContent()).extracting(CommunityRecord::getId).containsExactly(percentClub);
    }

    @Test
    void the_search_text_matches_underscore_literally() {

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, "a_b", Set.of(underscoreClub, plainClub)),
                PageRequest.of(0, 10));

        // then
        assertThat(actual.getContent()).extracting(CommunityRecord::getId).containsExactly(underscoreClub);
    }

    @Test
    void explore_excludes_the_memberships_and_counts_beyond_the_page() {

        // when
        var actual = sut.search(search(MembershipFilter.EXPLORE, Set.of(joinedGuild, joinedCircle, joinedSecondGuild)),
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
        assertThat(actual.getTotalElements()).isEqualTo(4);
    }

    private Long save(Long kindId, String organizationId, String slug, String name, CommunityStatus status) {

        return communityRepository.save(CommunityEntityMother.any().organizationId(organizationId).kindId(kindId)
                .slug(slug).name(name).status(status).build()).getId();
    }

    private static CommunitySearch search(MembershipFilter membership, Set<Long> memberOf) {

        return search(membership, "guild", memberOf);
    }

    private static CommunitySearch search(MembershipFilter membership, String name, Set<Long> memberOf) {

        return CommunitySearch.builder().organizationId("default").status(CommunityStatus.ACTIVE).name(name)
                .membership(membership).memberOf(memberOf).build();
    }
}
