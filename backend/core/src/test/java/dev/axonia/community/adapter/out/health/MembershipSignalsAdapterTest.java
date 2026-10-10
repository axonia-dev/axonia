package dev.axonia.community.adapter.out.health;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.adapter.out.persistence.CommunityEntityMother;
import dev.axonia.community.adapter.out.persistence.CommunityKindEntityMother;
import dev.axonia.community.adapter.out.persistence.CommunityKindRepository;
import dev.axonia.community.adapter.out.persistence.CommunityRepository;
import dev.axonia.community.adapter.out.persistence.JoinsPerBucketQuery;
import dev.axonia.community.adapter.out.persistence.MembershipRepository;
import dev.axonia.community.application.CommunityCapabilityService;
import dev.axonia.community.application.CommunityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MembershipSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private MembershipSignalsAdapter sut;

    private Long kindId;

    @BeforeEach
    void setUp() {

        kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
        sut = new MembershipSignalsAdapter(mock(CommunityService.class), mock(CommunityCapabilityService.class),
                membershipRepository, () -> "default");
    }

    @Test
    void joins_countsJoinedAtInWindow() {

        // given
        var communityId = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()).getId();
        membership(communityId, "alice", INSIDE);
        membership(communityId, "bob", INSIDE.plusSeconds(3600));
        membership(communityId, "carol", BEFORE);

        // when
        var actual = sut.joins(communityId, FROM, TO);

        // then
        assertThat(actual).isEqualTo(2);
    }

    @Test
    void memberCount_currentMembersOfTheCommunity() {

        // given
        var communityId = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()).getId();
        membership(communityId, "alice", INSIDE);
        membership(communityId, "bob", BEFORE);

        // when
        var actual = sut.memberCount(communityId);

        // then
        assertThat(actual).isEqualTo(2);
    }

    @Test
    void delegation_communityIdForSlug() {

        // given
        var communityService = mock(CommunityService.class);
        var delegating = new MembershipSignalsAdapter(communityService, mock(CommunityCapabilityService.class),
                membershipRepository, () -> "default");
        when(communityService.getBySlug("backend-guild"))
                .thenReturn(dev.axonia.community.api.CommunityMother.any().id(42L).build());

        // when / then
        assertThat(delegating.communityIdForSlug("backend-guild")).isEqualTo(42L);
    }

    @Test
    void delegation_canViewHealth() {

        // given
        var communityCapabilities = mock(CommunityCapabilityService.class);
        var delegating = new MembershipSignalsAdapter(mock(CommunityService.class), communityCapabilities,
                membershipRepository, () -> "default");
        when(communityCapabilities.canViewHealth(42L, "alice")).thenReturn(true);

        // when / then
        assertThat(delegating.canViewHealth(42L, "alice")).isTrue();
    }

    @Test
    void joinsPerBucket_placesEachJoinInItsOwnBucket() {

        // given a 12-day window bucketed by one day, with joins on day 0 and day 6 - each also
        // stamped with an unrelated updated_at (always "now") so a query that buckets on the wrong
        // timestamp column would be caught - plus a same-day join in a different community that
        // must not be counted
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        var communityId = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()).getId();
        var otherCommunityId = communityRepository
                .save(CommunityEntityMother.any().kindId(kindId).slug("frontend-guild").build()).getId();
        membership(communityId, "bob", from.plus(Duration.ofHours(1)));
        membership(communityId, "carol", from.plus(Duration.ofDays(6)));
        membership(otherCommunityId, "dana", from.plus(Duration.ofDays(6)));

        // when
        var actual = sut.joinsPerBucket(communityId, from, to, 12);

        // then
        assertThat(actual).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    void joinsPerBucket_putsAJoinAtTheLastTickOfTheWindowInTheFinalBucket() {

        // A join one millisecond before `to` - the closest a row inside the half-open window can sit
        // to its end - belongs in the last bucket, not off the end of the series.
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        var communityId = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()).getId();
        membership(communityId, "bob", to.minusMillis(1));

        assertThat(sut.joinsPerBucket(communityId, from, to, 12)).hasSize(12).element(11).isEqualTo(1L);
    }

    @Test
    void joinsPerBucket_ofAnEmptyCommunityIsTwelveZeroes() {

        var from = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(sut.joinsPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12)).hasSize(12)
                .allMatch(count -> count == 0L);
    }

    @Test
    void joinsPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        // The series must be one grouped query. A loop over twelve sub-windows would pass every
        // behavioural test above and cost twelve round trips.
        var repository = mock(MembershipRepository.class);
        var from = Instant.parse("2026-03-01T00:00:00Z");

        new MembershipSignalsAdapter(mock(CommunityService.class), mock(CommunityCapabilityService.class), repository,
                () -> "default").joinsPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12);

        verify(repository, times(1)).countJoinsPerBucket(any(JoinsPerBucketQuery.class), any(Instant.class),
                any(Instant.class));
        verifyNoMoreInteractions(repository);
    }

    private void membership(long communityId, String userId, Instant joinedAt) {

        jdbc.update("""
                INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, version)
                VALUES ('default', :communityId, :userId, 'MEMBER', :joinedAt, now(), 0)
                """, new MapSqlParameterSource().addValue("communityId", communityId).addValue("userId", userId)
                .addValue("joinedAt", Timestamp.from(joinedAt)));
    }
}
