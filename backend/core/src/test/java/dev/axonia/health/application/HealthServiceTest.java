package dev.axonia.health.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.health.api.BucketCredit;
import dev.axonia.health.api.BucketTalks;
import dev.axonia.health.api.Buckets;
import dev.axonia.health.api.OpeningCounts;
import dev.axonia.health.api.OrgUnitLabels;
import dev.axonia.health.api.ReuseCounts;
import dev.axonia.health.api.SeriesPulse;
import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.DirectorySignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.MembershipSignals;
import dev.axonia.health.api.spi.OpeningSignals;
import dev.axonia.health.api.spi.PresenceSignals;
import dev.axonia.health.api.spi.RecognitionSignals;
import dev.axonia.health.api.spi.ReuseSignals;
import dev.axonia.health.application.port.out.CurrentUser;
import dev.axonia.health.application.port.out.OrganizationContext;
import dev.axonia.health.domain.CadenceStatus;
import dev.axonia.health.domain.Finding;
import dev.axonia.health.domain.HealthSnapshot;
import dev.axonia.health.domain.Status;
import dev.axonia.health.domain.StatusCode;
import dev.axonia.health.domain.Trend;
import dev.axonia.shared.api.error.ForbiddenException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
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
    private static final List<Long> TWELVE_ZEROS = Collections.nCopies(Buckets.COUNT, 0L);

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
    }

    /**
     * A viewable community with every signal stubbed empty and everyone present, so a test overrides only the reads it
     * is about. Lenient: a test that overrides one read leaves its default unused.
     */
    private void givenAViewableCommunity() {

        organizer();
        givenQuietSignals();
    }

    private void givenQuietSignals() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(eventsSignals.talkCounts(anyLong(), any(), any())).thenReturn(Map.of());
        lenient().when(eventsSignals.topicProposals(anyLong(), any(), any())).thenReturn(Map.of());
        lenient().when(artifactSignals.artifactCounts(anyLong(), any(), any())).thenReturn(Map.of());
        lenient().when(eventsSignals.engagedUserIds(anyLong(), any(), any())).thenReturn(Set.of());
        lenient().when(presenceSignals.presentOf(eq("default"), any()))
                .thenAnswer(invocation -> Set.copyOf(invocation.<Collection<String>>getArgument(1)));
        lenient().when(eventsSignals.heldSessionDates(anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(eventsSignals.activeSeries(anyLong())).thenReturn(List.of());
        lenient().when(eventsSignals.nextSessionAt(anyLong(), any())).thenReturn(Optional.empty());
        lenient().when(eventsSignals.lastHeldSessionAt(anyLong(), any())).thenReturn(Optional.empty());
        lenient().when(recognitionSignals.lovesGiven(anyLong(), any(), any())).thenReturn(0L);
        lenient().when(membershipSignals.joins(anyLong(), any(), any())).thenReturn(0L);
        lenient().when(membershipSignals.memberCount(anyLong())).thenReturn(0L);
        lenient().when(membershipSignals.membersAmong(anyLong(), any())).thenReturn(Set.of());
        lenient().when(directorySignals.unitsOf(eq("default"), any())).thenReturn(Map.of());
        lenient().when(reuseSignals.reuseCounts(anyLong(), any(), any())).thenReturn(new ReuseCounts(0, 0, 0, 0));
        lenient().when(openingSignals.openingCounts(anyLong(), any(), any())).thenReturn(new OpeningCounts(0, 0));
        lenient().when(eventsSignals.talkCreditsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(List.of());
        lenient().when(eventsSignals.topicCreditsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(List.of());
        lenient().when(artifactSignals.artifactCreditsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(List.of());
        lenient().when(eventsSignals.engagementCreditsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(List.of());
        lenient().when(membershipSignals.joinsPerBucket(anyLong(), any(), any(), eq(Buckets.COUNT)))
                .thenReturn(TWELVE_ZEROS);
    }

    private static List<Long> bucketsOf(Map<Integer, Long> counts) {

        return Buckets.zeroFill(counts, Buckets.COUNT);
    }

    @Test
    void snapshot_assemblesTheDials() {

        // given
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 3L, "bob", 1L, "carol", 1L));
        when(eventsSignals.talkCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(Map.of("alice", 2L));
        when(eventsSignals.topicProposals(1L, FROM, NOW)).thenReturn(Map.of("orga", 4L));
        when(artifactSignals.artifactCounts(1L, FROM, NOW)).thenReturn(Map.of("orga", 2L, "bob", 1L));
        when(eventsSignals.engagedUserIds(1L, FROM, NOW)).thenReturn(Set.of("dana"));
        when(eventsSignals.engagedUserIds(1L, PREVIOUS_FROM, FROM)).thenReturn(Set.of("bob"));
        when(membershipSignals.membersAmong(1L, Set.of("alice", "bob", "carol", "orga", "dana")))
                .thenReturn(Set.of("alice", "dana"));
        when(directorySignals.unitsOf("default", Set.of("alice", "bob", "carol")))
                .thenReturn(Map.of("alice", new OrgUnitLabels("Acme", "Platform", "Payments"), "bob",
                        new OrgUnitLabels("Acme", "Platform", "Checkout"), "carol",
                        new OrgUnitLabels("Acme", "Mobile", "iOS")));
        when(directorySignals.unitsOf("default", Set.of("alice")))
                .thenReturn(Map.of("alice", new OrgUnitLabels("Acme", "Platform", "Payments")));
        when(eventsSignals.heldSessionDates(1L, FROM, NOW))
                .thenReturn(List.of(NOW.minus(Duration.ofDays(11)), NOW.minus(Duration.ofDays(4))));
        when(eventsSignals.heldSessionDates(1L, PREVIOUS_FROM, FROM))
                .thenReturn(List.of(NOW.minus(Duration.ofDays(100))));
        when(eventsSignals.activeSeries(1L)).thenReturn(List.of(new SeriesPulse(7)));
        when(eventsSignals.nextSessionAt(1L, NOW)).thenReturn(Optional.of(NOW.plus(Duration.ofDays(3))));
        when(eventsSignals.lastHeldSessionAt(1L, NOW)).thenReturn(Optional.of(NOW.minus(Duration.ofDays(4))));
        when(membershipSignals.memberCount(1L)).thenReturn(10L);
        when(recognitionSignals.lovesGiven(1L, FROM, NOW)).thenReturn(12L);
        when(recognitionSignals.lovesGiven(1L, PREVIOUS_FROM, FROM)).thenReturn(8L);
        when(membershipSignals.joins(1L, FROM, NOW)).thenReturn(3L);
        when(membershipSignals.joins(1L, PREVIOUS_FROM, FROM)).thenReturn(3L);
        when(reuseSignals.reuseCounts(1L, FROM, NOW)).thenReturn(new ReuseCounts(2, 1, 0, 0));
        when(openingSignals.openingCounts(1L, FROM, NOW)).thenReturn(new OpeningCounts(4, 2));
        when(openingSignals.openingCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(new OpeningCounts(2, 1));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then talks {alice 3, bob 1, carol 1}: alice alone gives more than half; curation {orga 6, bob 1};
        // involved = speakers, curators and dana's engagement = 5, up from {alice, bob}
        assertThat(actual).usingRecursiveComparison().ignoringFields("diagnosis", "series", "heldSessionDates")
                .isEqualTo(HealthSnapshot.builder().window("90d").generatedAt(NOW)
                        .stage(HealthSnapshot.StageDial.builder().talks(5).speakers(3).speakersForHalf(1)
                                .distribution(List.of(0.6, 0.2, 0.2)).trend(Trend.of(3, 1))
                                .status(Status.of(StatusCode.LEANS_ON_ONE)).build())
                        .curation(new HealthSnapshot.CurationDial(7, 6.0 / 7))
                        .cadence(HealthSnapshot.CadenceDial.builder().rhythm(CadenceStatus.STEADY).sessionsHeld(2)
                                .medianGapDays(7).nextSessionAt(NOW.plus(Duration.ofDays(3)))
                                .lastHeldAt(NOW.minus(Duration.ofDays(4))).trend(Trend.of(2, 1))
                                .status(Status.of(StatusCode.PICKING_UP)).build())
                        .people(new HealthSnapshot.PeopleDial(5, 2, Trend.of(5, 2)))
                        .engagement(new HealthSnapshot.EngagementDial(12, Trend.of(12, 8)))
                        .membershipGrowth(new HealthSnapshot.MembershipGrowthDial(3, 10, Trend.of(3, 3)))
                        .reach(new HealthSnapshot.CrossTeamReachDial(true, 3, 2, 1, 3, 3, Trend.of(3, 1)))
                        .reuse(HealthSnapshot.ReuseDial.builder().opened(4).artifactsOpened(2)
                                .openingsTrend(Trend.of(4, 2)).saves(2).artifactsSaved(1).helped(0).notHelped(0)
                                .savesTrend(Trend.of(2, 0)).status(Status.of(StatusCode.SAVED_NOT_CONFIRMED)).build())
                        .build());
        assertThat(actual.diagnosis().findings()).extracting(Finding::code).containsExactly(Finding.Code.OVER_RELIANCE,
                Finding.Code.CURATION_LEANS_ON_ONE, Finding.Code.INTEREST_WITHOUT_PAYOFF, Finding.Code.ON_RHYTHM);
    }

    @Test
    void snapshot_talksAndSpeakersExcludeDepartedPresenters() {

        // given
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 3L, "gone", 5L));
        when(presenceSignals.presentOf("default", Set.of("alice", "gone"))).thenReturn(Set.of("alice"));
        when(eventsSignals.talkCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT))
                .thenReturn(List.of(new BucketTalks(2, "alice", 3), new BucketTalks(5, "gone", 5)));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then the departed presenter leaves talks, speakers, half of the talks and the talks series together
        assertThat(actual.stage().talks()).isEqualTo(3);
        assertThat(actual.stage().speakers()).isEqualTo(1);
        assertThat(actual.stage().speakersForHalf()).isEqualTo(1);
        assertThat(actual.series().talks()).containsExactlyElementsOf(bucketsOf(Map.of(2, 3L)));
        assertThat(actual.reach().totalSpeakers()).isEqualTo(1);
        assertThat(actual.people().involved()).isEqualTo(1);
    }

    @Test
    void snapshot_peopleInvolvedUnionsAllActsAndCountsMembers() {

        // given alice talks, bob proposes a topic, carol captures an artifact and dan votes
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 1L));
        when(eventsSignals.topicProposals(1L, FROM, NOW)).thenReturn(Map.of("bob", 1L));
        when(artifactSignals.artifactCounts(1L, FROM, NOW)).thenReturn(Map.of("carol", 1L));
        when(eventsSignals.engagedUserIds(1L, FROM, NOW)).thenReturn(Set.of("dan"));
        when(membershipSignals.membersAmong(1L, Set.of("alice", "bob", "carol", "dan")))
                .thenReturn(Set.of("alice", "dan"));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then a count of people, never a share of members
        assertThat(actual.people()).isEqualTo(new HealthSnapshot.PeopleDial(4, 2, Trend.of(4, 0)));
        assertThat(actual.curation()).isEqualTo(new HealthSnapshot.CurationDial(2, 0.5));
    }

    @Test
    void snapshot_reachCountsSpeakersOnly() {

        // given alice speaks, bob only curates
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 2L));
        when(eventsSignals.topicProposals(1L, FROM, NOW)).thenReturn(Map.of("bob", 4L));
        when(directorySignals.unitsOf("default", Set.of("alice")))
                .thenReturn(Map.of("alice", new OrgUnitLabels("Acme", "Platform", "Payments")));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.reach())
                .isEqualTo(new HealthSnapshot.CrossTeamReachDial(true, 1, 1, 1, 1, 1, Trend.of(1, 0)));
        verify(directorySignals, never()).unitsOf(eq("default"), argThat(ids -> ids.contains("bob")));
    }

    @Test
    void snapshot_passesNextSessionToCadence() {

        // given a single one-off session 80 days ago and the next one booked in 10 days
        givenAViewableCommunity();
        var next = NOW.plus(Duration.ofDays(10));
        when(eventsSignals.heldSessionDates(1L, FROM, NOW)).thenReturn(List.of(NOW.minus(Duration.ofDays(80))));
        when(eventsSignals.nextSessionAt(1L, NOW)).thenReturn(Optional.of(next));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.cadence().rhythm()).isEqualTo(CadenceStatus.STEADY);
        assertThat(actual.cadence().nextSessionAt()).isEqualTo(next);
        assertThat(actual.cadence().status()).isEqualTo(Status.of(StatusCode.ON_RHYTHM));
    }

    @Test
    void snapshot_brandNewCommunityWithASessionBookedIsStartingUp() {

        // given no session ever held, no series, and a first session booked
        givenAViewableCommunity();
        when(eventsSignals.nextSessionAt(1L, NOW)).thenReturn(Optional.of(NOW.plus(Duration.ofDays(7))));
        when(eventsSignals.engagedUserIds(1L, PREVIOUS_FROM, FROM)).thenReturn(Set.of("early-voter"));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.cadence().rhythm()).isEqualTo(CadenceStatus.STALLED);
        assertThat(actual.cadence().status()).isEqualTo(Status.of(StatusCode.STARTING_UP));
        assertThat(actual.diagnosis().findings()).extracting(Finding::code).doesNotContain(Finding.Code.FADING,
                Finding.Code.DORMANT);
    }

    @Test
    void snapshot_bookedAfterALongSilenceIsStalledNotStartingUp() {

        // given nothing held in either window, but a session held long before them, and one booked
        givenAViewableCommunity();
        when(eventsSignals.nextSessionAt(1L, NOW)).thenReturn(Optional.of(NOW.plus(Duration.ofDays(7))));
        when(eventsSignals.lastHeldSessionAt(1L, NOW)).thenReturn(Optional.of(NOW.minus(Duration.ofDays(300))));

        // when
        var actual = sut().snapshot("backend-guild", 30);

        // then
        assertThat(actual.cadence().rhythm()).isEqualTo(CadenceStatus.STALLED);
        assertThat(actual.cadence().lastHeldAt()).isEqualTo(NOW.minus(Duration.ofDays(300)));
        assertThat(actual.cadence().status()).isEqualTo(Status.of(StatusCode.STALLED));
    }

    @Test
    void snapshot_helpedWithNoOpeningsStillPaysOff() {

        // given helped answers on old saves and no openings this window
        givenAViewableCommunity();
        when(reuseSignals.reuseCounts(1L, FROM, NOW)).thenReturn(new ReuseCounts(0, 0, 3, 0));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.reuse().status()).isEqualTo(Status.of(StatusCode.PAYING_OFF));
    }

    @Test
    void snapshot_peopleSeriesIsDistinctPresentPeoplePerBucket() {

        // given alice talks and proposes in bucket 1 where bob also proposes; carol captures and attends in bucket 3
        // where a departed person also attends
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("alice", 2L));
        when(eventsSignals.topicProposals(1L, FROM, NOW)).thenReturn(Map.of("alice", 1L, "bob", 1L));
        when(artifactSignals.artifactCounts(1L, FROM, NOW)).thenReturn(Map.of("carol", 1L));
        when(eventsSignals.engagedUserIds(1L, FROM, NOW)).thenReturn(Set.of("carol", "gone"));
        when(presenceSignals.presentOf("default", Set.of("alice", "bob", "carol", "gone")))
                .thenReturn(Set.of("alice", "bob", "carol"));
        when(eventsSignals.talkCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT))
                .thenReturn(List.of(new BucketTalks(1, "alice", 2)));
        when(eventsSignals.topicCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT))
                .thenReturn(List.of(new BucketCredit(1, "alice"), new BucketCredit(1, "bob")));
        when(artifactSignals.artifactCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT))
                .thenReturn(List.of(new BucketCredit(3, "carol")));
        when(eventsSignals.engagementCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT))
                .thenReturn(List.of(new BucketCredit(3, "carol"), new BucketCredit(3, "gone")));
        var joins = List.of(0L, 0L, 1L, 0L, 0L, 0L, 0L, 2L, 0L, 0L, 0L, 1L);
        when(membershipSignals.joinsPerBucket(1L, FROM, NOW, Buckets.COUNT)).thenReturn(joins);

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.series().talks()).containsExactlyElementsOf(bucketsOf(Map.of(1, 2L)));
        assertThat(actual.series().people()).containsExactlyElementsOf(bucketsOf(Map.of(1, 2L, 3, 1L)));
        assertThat(actual.series().joins()).containsExactlyElementsOf(joins);
    }

    @Test
    void snapshot_bucketsTheCurrentWindowOnlyAndNeverThePreviousOne() {

        // given
        givenAViewableCommunity();

        // when
        sut().snapshot("backend-guild", 90);

        // then
        verify(eventsSignals).talkCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT);
        verify(eventsSignals, never()).talkCreditsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
        verify(eventsSignals).topicCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT);
        verify(eventsSignals, never()).topicCreditsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
        verify(artifactSignals).artifactCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT);
        verify(artifactSignals, never()).artifactCreditsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
        verify(eventsSignals).engagementCreditsPerBucket(1L, FROM, NOW, Buckets.COUNT);
        verify(eventsSignals, never()).engagementCreditsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
        verify(membershipSignals).joinsPerBucket(1L, FROM, NOW, Buckets.COUNT);
        verify(membershipSignals, never()).joinsPerBucket(anyLong(), eq(PREVIOUS_FROM), eq(FROM), anyInt());
    }

    @Test
    void snapshot_peopleTrendComparesFilteredCurrentAgainstFilteredPrevious() {

        // given a present speaker now, and that speaker plus a since-departed one in the previous window
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(1L, FROM, NOW)).thenReturn(Map.of("still-here", 5L));
        when(eventsSignals.talkCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(Map.of("still-here", 5L, "departed", 5L));
        when(presenceSignals.presentOf("default", Set.of("still-here", "departed"))).thenReturn(Set.of("still-here"));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then a drop that never happened is not shown
        assertThat(actual.people().trend()).isEqualTo(Trend.of(1, 1));
        assertThat(actual.stage().trend()).isEqualTo(Trend.of(1, 1));
    }

    @Test
    void snapshot_noActivityIsAnHonestZero() {

        // given
        givenAViewableCommunity();

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.stage().talks()).isZero();
        assertThat(actual.stage().distribution()).isEmpty();
        assertThat(actual.stage().status()).isEqualTo(Status.of(StatusCode.TOO_FEW_TALKS));
        assertThat(actual.cadence().rhythm()).isEqualTo(CadenceStatus.DORMANT);
        assertThat(actual.cadence().status()).isEqualTo(Status.of(StatusCode.DORMANT));
        assertThat(actual.cadence().nextSessionAt()).isNull();
        assertThat(actual.people()).isEqualTo(new HealthSnapshot.PeopleDial(0, 0, Trend.of(0, 0)));
        assertThat(actual.curation()).isEqualTo(new HealthSnapshot.CurationDial(0, 0.0));
        assertThat(actual.reuse().status()).isEqualTo(Status.of(StatusCode.NOTHING_OPENED));
        assertThat(actual.series().talks()).containsExactlyElementsOf(TWELVE_ZEROS);
        assertThat(actual.series().people()).containsExactlyElementsOf(TWELVE_ZEROS);
    }

    @Test
    void snapshot_allowedWhenHealthOpenToMember() {

        // given
        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(currentUser.userId()).thenReturn("alice");
        when(membershipSignals.canViewHealth(1L, "alice")).thenReturn(true);
        givenQuietSignals();

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
    void snapshot_serializedSnapshotNeverContainsAUserId() {

        // given distinctive userIds and unit names entering through every per-person signal
        givenAViewableCommunity();
        when(eventsSignals.talkCounts(anyLong(), any(), any()))
                .thenReturn(Map.of("uid-alice-7f3", 5L, "uid-bob-9c1", 2L));
        when(artifactSignals.artifactCounts(anyLong(), any(), any())).thenReturn(Map.of("uid-carol-2aa", 1L));
        when(eventsSignals.engagedUserIds(anyLong(), any(), any())).thenReturn(Set.of("uid-dana-5e0"));
        when(membershipSignals.membersAmong(anyLong(), any())).thenReturn(Set.of("uid-dana-5e0"));
        when(directorySignals.unitsOf(eq("default"), any()))
                .thenReturn(Map.of("uid-alice-7f3", new OrgUnitLabels("BU-secret", "Dept-secret", "Team-secret")));
        when(eventsSignals.talkCreditsPerBucket(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(new BucketTalks(0, "uid-alice-7f3", 5)));
        when(eventsSignals.engagementCreditsPerBucket(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(new BucketCredit(1, "uid-dana-5e0")));

        // when
        var json = JsonMapper.builder().build().writeValueAsString(sut().snapshot("backend-guild", 90));

        // then identity and unit names entered the math and never left the core
        assertThat(json).doesNotContain("uid-alice-7f3").doesNotContain("uid-bob-9c1").doesNotContain("uid-carol-2aa")
                .doesNotContain("uid-dana-5e0").doesNotContain("orga").doesNotContain("BU-secret")
                .doesNotContain("Dept-secret").doesNotContain("Team-secret");
    }

    @Test
    void snapshot_carriesTheRealHeldSessionDatesRatherThanOnlyTheirCount() {

        // given
        var first = NOW.minus(Duration.ofDays(40));
        var second = NOW.minus(Duration.ofDays(12));
        givenAViewableCommunity();
        when(eventsSignals.heldSessionDates(1L, FROM, NOW)).thenReturn(List.of(first, second));

        // when / then
        assertThat(sut().snapshot("backend-guild", 90).heldSessionDates()).containsExactly(first, second);
    }

    @Test
    void snapshot_reportsOpeningsAboveDemandWithTheirOwnTrend() {

        // given
        givenAViewableCommunity();
        when(openingSignals.openingCounts(1L, FROM, NOW)).thenReturn(new OpeningCounts(40L, 12L));
        when(openingSignals.openingCounts(1L, PREVIOUS_FROM, FROM)).thenReturn(new OpeningCounts(31L, 9L));

        // when
        var actual = sut().snapshot("backend-guild", 90);

        // then
        assertThat(actual.reuse().opened()).isEqualTo(40L);
        assertThat(actual.reuse().artifactsOpened()).isEqualTo(12L);
        assertThat(actual.reuse().openingsTrend()).isEqualTo(Trend.of(40L, 31L));
        assertThat(actual.reuse().savesTrend()).isEqualTo(Trend.of(0L, 0L));
        assertThat(actual.reuse().status()).isEqualTo(Status.of(StatusCode.NO_PAYOFF_YET));
    }
}
