package dev.axonia.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ForbiddenException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class HealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");
    private static final Instant FROM = NOW.minus(Duration.ofDays(90));
    private static final Instant PREVIOUS_FROM = NOW.minus(Duration.ofDays(180));

    @Mock
    private EventsSignals eventsSignals;

    @Mock
    private ArtifactSignals artifactSignals;

    @Mock
    private ReuseSignals reuseSignals;

    @Mock
    private RecognitionSignals recognitionSignals;

    @Mock
    private MembershipSignals membershipSignals;

    @Mock
    private DirectorySignals directorySignals;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private PresenceSignals presenceSignals;

    @Mock
    private OpeningSignals openingSignals;

    private HealthService sut() {

        return new HealthService(eventsSignals, artifactSignals, reuseSignals, recognitionSignals, membershipSignals,
                directorySignals, organizationContext, currentUser, presenceSignals, openingSignals,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void organizer() {

        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("orga");
        when(membershipSignals.canViewHealth(1L, "orga")).thenReturn(true);
        when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void snapshot_assemblesAllFiveSignals() {

        // given
        organizer();
        when(eventsSignals.contributionCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 5L, "bob", 2L, "carol", 2L));
        when(artifactSignals.artifactCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 1L));
        when(eventsSignals.contributionCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(Map.of("alice", 4L));
        when(artifactSignals.artifactCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(Map.of());
        when(directorySignals.unitsOf("default", Set.of("alice", "bob", "carol")))
                .thenReturn(Map.of("alice", new OrgUnitLabels("Acme", "Platform", "Payments"), "bob",
                        new OrgUnitLabels("Acme", "Platform", "Checkout"), "carol",
                        new OrgUnitLabels("Acme", "Mobile", "iOS")));
        when(directorySignals.unitsOf("default", Set.of("alice")))
                .thenReturn(Map.of("alice", new OrgUnitLabels("Acme", "Platform", "Payments")));
        var held = List.of(NOW.minus(Duration.ofDays(11)), NOW.minus(Duration.ofDays(4)));
        when(eventsSignals.heldSessionDates(1L, FROM, NOW)).thenReturn(held);
        when(eventsSignals.heldSessionDates(1L, PREVIOUS_FROM, FROM))
                .thenReturn(List.of(NOW.minus(Duration.ofDays(100))));
        when(eventsSignals.activeSeries(1L)).thenReturn(List.of(new SeriesPulse(7)));
        when(eventsSignals.engagedUserIds(1L, FROM, NOW)).thenReturn(Set.of("bob", "dana"));
        when(eventsSignals.engagedUserIds(1L, PREVIOUS_FROM, FROM)).thenReturn(Set.of("bob"));
        // presence is checked over contributors ∪ engaged, so dana (engaged-only) and bob (both) are included
        when(presenceSignals.presentOf("default", Set.of("alice", "bob", "carol", "dana")))
                .thenReturn(Set.of("alice", "bob", "carol", "dana"));
        when(presenceSignals.presentOf("default", Set.of("alice", "bob"))).thenReturn(Set.of("alice", "bob"));
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(recognitionSignals.lovesGiven(1L, FROM, NOW)).thenReturn(12L);
        when(recognitionSignals.lovesGiven(1L, PREVIOUS_FROM, FROM)).thenReturn(8L);
        when(membershipSignals.joins(1L, FROM, NOW)).thenReturn(3L);
        when(membershipSignals.joins(1L, PREVIOUS_FROM, FROM)).thenReturn(3L);
        when(reuseSignals.reuseCounts(1L, FROM, NOW)).thenReturn(new ReuseCounts(0, 0, 0, 0));
        when(reuseSignals.reuseCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(new ReuseCounts(0, 0, 0, 0));
        when(openingSignals.openingCounts(1L, FROM, NOW)).thenReturn(new OpeningCounts(0, 0));
        when(openingSignals.openingCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(new OpeningCounts(0, 0));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then merged counts {alice 6, bob 2, carol 2} of 10 acts -> alice alone crosses 50%
        // series and heldSessionDates are covered by their own dedicated tests below
        assertThat(actual).usingRecursiveComparison().ignoringFields("reuse", "diagnosis", "series", "heldSessionDates")
                .isEqualTo(HealthSnapshot.builder().window("90d").generatedAt(NOW).busFactor(1)
                        .concentration(new HealthSnapshot.Concentration(1.0, List.of(0.6, 0.2, 0.2)))
                        .cadence(new HealthSnapshot.CadenceDial(CadenceStatus.STEADY, 2, 7, Trend.of(2, 1)))
                        // active = contributors {alice,bob,carol} ∪ engaged {bob,dana} = 4 of 10
                        .participation(new HealthSnapshot.ParticipationDial(4, 10, 0.4, Trend.of(0.4, 0.2)))
                        .engagement(new HealthSnapshot.EngagementDial(12, Trend.of(12, 8)))
                        .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(3, 10, Trend.of(3, 3)))
                        .reach(new HealthSnapshot.CrossTeamReachDial(true, 3, 2, 1, 3, 3, Trend.of(3, 1))).build());
    }

    @Test
    void busFactorIgnoresContributorsWhoHaveLeft() {

        // given
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        // two contributors with equal share; one has left, so the survivor alone carries the community
        when(eventsSignals.contributionCounts(1L, FROM, NOW)).thenReturn(Map.of("still-here", 5L, "departed", 5L));
        when(presenceSignals.presentOf("default", Set.of("still-here", "departed"))).thenReturn(Set.of("still-here"));

        // when
        var snapshot = sut().snapshot("backend-guild", 90);

        // then
        assertThat(snapshot.busFactor()).isEqualTo(1);
        assertThat(snapshot.reach().totalContributors()).isEqualTo(1);
    }

    @Test
    void participationIgnoresEngagedMembersWhoHaveLeft() {

        // given a present contributor, and a departed member who shows up only via engagement, never as a contributor
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(eventsSignals.contributionCounts(1L, FROM, NOW)).thenReturn(Map.of("still-here", 5L));
        when(eventsSignals.engagedUserIds(1L, FROM, NOW)).thenReturn(Set.of("still-here", "departed-engager"));
        when(presenceSignals.presentOf("default", Set.of("still-here", "departed-engager")))
                .thenReturn(Set.of("still-here"));

        // when
        var snapshot = sut().snapshot("backend-guild", 90);

        // then the departed member does not re-enter active membership through engagement
        assertThat(snapshot.participation().activeMembers()).isEqualTo(1);
    }

    @Test
    void trendComparesFilteredCurrentAgainstFilteredPrevious() {

        // given a present contributor, joined in the previous window by a contributor who has since departed
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(eventsSignals.contributionCounts(1L, FROM, NOW)).thenReturn(Map.of("still-here", 5L));
        when(presenceSignals.presentOf("default", Set.of("still-here"))).thenReturn(Set.of("still-here"));
        when(eventsSignals.contributionCounts(1L, PREVIOUS_FROM, FROM))
                .thenReturn(Map.of("still-here", 5L, "departed", 5L));
        when(presenceSignals.presentOf("default", Set.of("still-here", "departed"))).thenReturn(Set.of("still-here"));

        // when
        var snapshot = sut().snapshot("backend-guild", 90);

        // then the previous window's departed contributor is dropped before the trend is computed, so participation
        // reads flat rather than showing a drop that never happened
        assertThat(snapshot.participation().trend()).isEqualTo(Trend.of(0.1, 0.1));
    }

    @Test
    void snapshot_allowedWhenOrganizerCanViewHealth() {

        // given
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(0L);

        // when / then
        assertThat(sut().snapshot("backend-guild", 90)).isNotNull();
    }

    @Test
    void snapshot_allowedWhenHealthOpenToMember() {

        // given
        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("alice");
        when(membershipSignals.canViewHealth(1L, "alice")).thenReturn(true);
        when(organizationContext.organizationId()).thenReturn("default");
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(0L);

        // when / then
        assertThat(sut().snapshot("backend-guild", 90)).isNotNull();
    }

    @Test
    void snapshot_deniedWhenHealthClosedToMember() {

        // given
        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("mallory");
        when(membershipSignals.canViewHealth(1L, "mallory")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut().snapshot("backend-guild", 90)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void snapshot_noActivityIsAnHonestZero() {

        // given a community with members but no acts at all
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(8L);

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.busFactor()).isZero();
        assertThat(actual.concentration().distribution()).isEmpty();
        assertThat(actual.cadence().status()).isEqualTo(CadenceStatus.DORMANT);
        assertThat(actual.participation().share()).isZero();
    }

    @Test
    void snapshot_zeroMembersDoesNotDivide() {

        // given
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(0L);

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.participation().share()).isZero();
    }

    @Test
    void snapshot_serializedSnapshotNeverContainsAUserId() {

        // given distinctive userIds and unit names entering through every per-member signal
        organizer();
        when(eventsSignals.contributionCounts(anyLong(), any(), any()))
                .thenReturn(Map.of("uid-alice-7f3", 5L, "uid-bob-9c1", 2L));
        when(artifactSignals.artifactCounts(anyLong(), any(), any())).thenReturn(Map.of("uid-carol-2aa", 1L));
        when(eventsSignals.engagedUserIds(anyLong(), any(), any())).thenReturn(Set.of("uid-dana-5e0"));
        when(eventsSignals.heldSessionDates(anyLong(), any(), any())).thenReturn(List.of());
        when(eventsSignals.activeSeries(anyLong())).thenReturn(List.of());
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(directorySignals.unitsOf(eq("default"), any()))
                .thenReturn(Map.of("uid-alice-7f3", new OrgUnitLabels("BU-secret", "Dept-secret", "Team-secret")));
        when(reuseSignals.reuseCounts(anyLong(), any(), any())).thenReturn(new ReuseCounts(0, 0, 0, 0));
        when(openingSignals.openingCounts(anyLong(), any(), any())).thenReturn(new OpeningCounts(0, 0));
        when(presenceSignals.presentOf(eq("default"), any()))
                .thenReturn(Set.of("uid-alice-7f3", "uid-bob-9c1", "uid-carol-2aa", "uid-dana-5e0"));

        // when
        var json = JsonMapper.builder().build().writeValueAsString(sut().snapshot("backend-guild", 90));

        // then identity AND unit names entered the math and never left the core
        assertThat(json).doesNotContain("uid-alice-7f3").doesNotContain("uid-bob-9c1").doesNotContain("uid-carol-2aa")
                .doesNotContain("uid-dana-5e0").doesNotContain("orga").doesNotContain("BU-secret")
                .doesNotContain("Dept-secret").doesNotContain("Team-secret");
    }

    private void stubEmptySignals() {

        when(eventsSignals.contributionCounts(anyLong(), any(), any())).thenReturn(Map.of());
        when(artifactSignals.artifactCounts(anyLong(), any(), any())).thenReturn(Map.of());
        when(eventsSignals.heldSessionDates(anyLong(), any(), any())).thenReturn(List.of());
        when(eventsSignals.activeSeries(anyLong())).thenReturn(List.of());
        when(eventsSignals.engagedUserIds(anyLong(), any(), any())).thenReturn(Set.of());
        when(recognitionSignals.lovesGiven(anyLong(), any(), any())).thenReturn(0L);
        when(membershipSignals.joins(anyLong(), any(), any())).thenReturn(0L);
        when(organizationContext.organizationId()).thenReturn("default");
        when(directorySignals.unitsOf(eq("default"), any())).thenReturn(Map.of());
        when(reuseSignals.reuseCounts(anyLong(), any(), any())).thenReturn(new ReuseCounts(0, 0, 0, 0));
        when(openingSignals.openingCounts(anyLong(), any(), any())).thenReturn(new OpeningCounts(0, 0));
    }

    /**
     * A viewable community with every scalar signal stubbed empty, plus a zero-filled twelve-entry default for every
     * bucketed series read, so a test that overrides only the bucketed reads it cares about does not have to restub
     * everything else.
     */
    private void givenAViewableCommunity() {

        organizer();
        stubEmptySignals();
        var twelveZeros = Collections.nCopies(Buckets.COUNT, 0L);
        // lenient: a test asserting one bucketed dial legitimately overrides only that one, leaving these defaults
        // unused
        lenient().when(eventsSignals.distinctContributorsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(twelveZeros);
        lenient().when(eventsSignals.engagedMembersPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(twelveZeros);
        lenient().when(recognitionSignals.lovesPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(twelveZeros);
        lenient().when(membershipSignals.joinsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(twelveZeros);
        lenient().when(openingSignals.openingsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(twelveZeros);
        lenient().when(reuseSignals.savesPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT))).thenReturn(twelveZeros);
    }

    @Test
    void snapshot_carriesTwelvePointSeriesForEveryBucketedDial() {

        // given six distinct series, so a crossed wire between any two ports fails loudly and by name
        var contributors = List.of(1L, 0L, 2L, 0L, 0L, 1L, 0L, 0L, 3L, 0L, 0L, 1L);
        var engagedMembers = List.of(4L, 4L, 5L, 5L, 5L, 6L, 6L, 6L, 7L, 7L, 7L, 8L);
        var loves = List.of(0L, 1L, 1L, 2L, 3L, 5L, 8L, 8L, 9L, 9L, 10L, 12L);
        var joins = List.of(1L, 0L, 0L, 1L, 0L, 0L, 0L, 2L, 0L, 0L, 0L, 1L);
        var openings = List.of(2L, 3L, 1L, 0L, 4L, 6L, 5L, 7L, 8L, 2L, 1L, 9L);
        var saves = List.of(0L, 1L, 0L, 0L, 1L, 2L, 1L, 0L, 3L, 0L, 0L, 2L);
        givenAViewableCommunity();
        when(eventsSignals.distinctContributorsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(contributors);
        when(eventsSignals.engagedMembersPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(engagedMembers);
        when(recognitionSignals.lovesPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT))).thenReturn(loves);
        when(membershipSignals.joinsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT))).thenReturn(joins);
        when(openingSignals.openingsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT))).thenReturn(openings);
        when(reuseSignals.savesPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT))).thenReturn(saves);

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then each series carries exactly the values read from its own port, never another's
        assertThat(actual.series().contributors()).containsExactlyElementsOf(contributors);
        assertThat(actual.series().participation()).containsExactlyElementsOf(engagedMembers);
        assertThat(actual.series().engagement()).containsExactlyElementsOf(loves);
        assertThat(actual.series().joins()).containsExactlyElementsOf(joins);
        assertThat(actual.series().openings()).containsExactlyElementsOf(openings);
        assertThat(actual.series().saves()).containsExactlyElementsOf(saves);
    }

    @Test
    void snapshot_carriesTheRealHeldSessionDatesRatherThanOnlyTheirCount() {

        // The dates were already read to count them and then discarded; the pulse needs them.
        var first = NOW.minus(Duration.ofDays(40));
        var second = NOW.minus(Duration.ofDays(12));
        givenAViewableCommunity();
        when(eventsSignals.heldSessionDates(1L, FROM, NOW)).thenReturn(List.of(first, second));

        // when / then
        assertThat(sut().snapshot("backend-guild", 90).heldSessionDates()).containsExactly(first, second);
    }

    @Test
    void snapshot_bucketsTheCurrentWindowOnlyAndNeverThePreviousOne() {

        // The series describes this window. The previous window keeps its scalar comparison and
        // must not be bucketed, or the request cost doubles for a line nothing draws - checked for
        // all six bucketed reads, not only one, so a future regression on any of them is caught.
        givenAViewableCommunity();

        // when
        sut().snapshot("backend-guild", 90);

        // then
        verify(eventsSignals).distinctContributorsPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(eventsSignals, never()).distinctContributorsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());

        verify(eventsSignals).engagedMembersPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(eventsSignals, never()).engagedMembersPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());

        verify(recognitionSignals).lovesPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(recognitionSignals, never()).lovesPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());

        verify(membershipSignals).joinsPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(membershipSignals, never()).joinsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());

        verify(openingSignals).openingsPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(openingSignals, never()).openingsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());

        verify(reuseSignals).savesPerBucket(anyLong(), eq(FROM), eq(NOW), eq(Buckets.COUNT));
        verify(reuseSignals, never()).savesPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
    }

    @Test
    void snapshot_reportsReuseAndFlagsInterestWithoutPayoff() {

        // given interest but no confirmed payoff
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(reuseSignals.reuseCounts(1L, FROM, NOW)).thenReturn(new ReuseCounts(18, 7, 0, 0));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.reuse().saves()).isEqualTo(18);
        assertThat(actual.reuse().artifactsSaved()).isEqualTo(7);
        assertThat(actual.diagnosis().findings()).extracting(Finding::code)
                .contains(Finding.Code.INTEREST_WITHOUT_PAYOFF);
    }

    @Test
    void snapshot_reportsOpeningsAboveDemandWithTheirOwnTrend() {

        // given
        organizer();
        stubEmptySignals();
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(openingSignals.openingCounts(1L, FROM, NOW)).thenReturn(new OpeningCounts(40L, 12L));
        when(openingSignals.openingCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(new OpeningCounts(31L, 9L));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.reuse().opened()).isEqualTo(40L);
        assertThat(actual.reuse().artifactsOpened()).isEqualTo(12L);
        assertThat(actual.reuse().openingsTrend()).isEqualTo(Trend.of(40L, 31L));
        assertThat(actual.reuse().savesTrend()).isEqualTo(Trend.of(0L, 0L));
    }
}
